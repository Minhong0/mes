package com.minimes.lot.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.mapper.LotTraceMapper;
import com.minimes.lot.mapper.TraceNode;
import com.minimes.lot.repository.LotRepository;
import com.minimes.lot.service.LotHoldService.HeldLot;
import com.minimes.lot.service.LotHoldService.HoldCommand;
import com.minimes.lot.service.LotHoldService.HoldResult;
import com.minimes.lot.service.LotService.ReceiveCommand;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.service.OperationService;
import com.minimes.workorder.service.OperationService.ReportCommand;
import com.minimes.workorder.service.WorkOrderService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 품질 보류·해제: 보류한 LOT은 투입·취소를 막고, 해제하면 다시 쓸 수 있다.
 * 하위 LOT을 찾는 정방향 추적(재귀 CTE)은 MSSQL 전용이라 여기서는 결과를 정해 주고, 실제 쿼리는 MssqlIntegrationTest가 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql", "/sql/lot-fixture.sql"})
class LotHoldTest {

    static final String QC = "W6001";

    @MockitoBean
    LotTraceMapper traceMapper;

    @Autowired
    LotHoldService holdService;

    @Autowired
    LotService lotService;

    @Autowired
    OperationService operationService;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    LotRepository lotRepository;

    WorkOrder wo;
    long op10;
    long op20;

    @BeforeEach
    void createWorkOrder() {
        wo = workOrderService.create("FG-SFT-100", 100, null, null);
        op10 = wo.getOperations().get(0).getWoOpId();
        op20 = wo.getOperations().get(1).getWoOpId();
        when(traceMapper.selectForward(anyString())).thenReturn(List.of());
    }

    Lot receive(int qty) {
        return lotService.receive(new ReceiveCommand("RM-SCM-001", BigDecimal.valueOf(qty), null, "W1001"));
    }

    WorkResult report(long woOpId, int good, String lotNo, int qty) {
        return operationService.report(woOpId, new ReportCommand(UUID.randomUUID().toString(), "W1001", null, good,
                List.of(), List.of(new LotInput(lotNo, BigDecimal.valueOf(qty)))));
    }

    String outputOf(WorkResult result) {
        return lotService.resultLots(List.of(result.getResultId())).get(result.getResultId()).outputLotNos().getFirst();
    }

    Lot lot(String lotNo) {
        return lotRepository.findById(lotNo).orElseThrow();
    }

    /** 정방향 추적 결과를 정해 둔다 */
    void forward(String from, String... lotNos) {
        when(traceMapper.selectForward(from)).thenReturn(nodes(lotNos));
    }

    static List<TraceNode> nodes(String... lotNos) {
        return Arrays.stream(lotNos)
                .map(n -> new TraceNode(1, null, n, null, null, null, null, null, BigDecimal.ONE, null, null, null, null))
                .toList();
    }

    static HoldCommand by(String workerId, boolean downstream) {
        return new HoldCommand(workerId, "치수 불량 의심", downstream);
    }

    void expectError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("환봉 LOT을 하위까지 보류: 남은 수량이 있는 LOT은 모두 HOLD, 이력에 원인 LOT이 남고, 투입 후보에서 빠진다")
    void holdDownstream() {
        Lot bar = receive(10);
        operationService.start(op10, "W1001");
        String turned = outputOf(report(op10, 5, bar.getLotNo(), 5));
        operationService.start(op20, "W1001");
        String milled = outputOf(report(op20, 3, turned, 3));
        forward(bar.getLotNo(), turned, milled);

        HoldResult result = holdService.hold(bar.getLotNo(), by(QC, true));

        assertThat(result.changed()).extracting(Lot::getLotNo).containsExactly(bar.getLotNo(), turned, milled);
        assertThat(result.skipped()).isEmpty();
        assertThat(lot(turned).getStatus()).isEqualTo(LotStatus.HOLD);
        assertThat(lotService.detail(turned).history())
                .extracting(LotHistory::getEventType, LotHistory::getNote)
                .endsWith(tuple(LotEventType.HOLD, "치수 불량 의심 (원인 LOT " + bar.getLotNo() + ")"));
        assertThat(lotService.detail(bar.getLotNo()).history().getLast().getNote()).isEqualTo("치수 불량 의심");

        assertThat(lotService.inputCandidates(wo.getOperations().get(0)).getFirst().lots()).isEmpty();
        assertThat(lotService.inputCandidates(wo.getOperations().get(1)).getFirst().lots()).isEmpty();
    }

    @Test
    @DisplayName("보류 목록: 원인 LOT과 사유를 나눠 주고(하위 LOT은 원인 LOT, 시작 LOT은 자기 자신), 검사 불합격을 가린다. 해제하면 빠진다")
    void heldList() {
        Lot bar = receive(10);
        operationService.start(op10, "W1001");
        String turned = outputOf(report(op10, 5, bar.getLotNo(), 5));
        forward(bar.getLotNo(), turned);
        holdService.hold(bar.getLotNo(), by(QC, true));
        Lot other = receive(3);
        holdService.holdByRule(other.getLotNo(), "W1001", LotHoldService.INSPECTION_FAILED + ": 외경 #2 38.07 (상한 38.05)");

        List<HeldLot> held = holdService.heldLots();

        assertThat(held).extracting(h -> h.lot().getLotNo(), HeldLot::originLotNo, HeldLot::reason, HeldLot::byInspection)
                .containsExactlyInAnyOrder(
                        tuple(bar.getLotNo(), bar.getLotNo(), "치수 불량 의심", false),
                        tuple(turned, bar.getLotNo(), "치수 불량 의심", false),
                        tuple(other.getLotNo(), other.getLotNo(), "검사 불합격: 외경 #2 38.07 (상한 38.05)", true));
        assertThat(held).allSatisfy(h -> assertThat(h.heldAt()).isNotNull());
        assertThat(held).filteredOn(h -> h.lot().getLotNo().equals(turned)).singleElement()
                .extracting(HeldLot::heldBy).isEqualTo(QC);

        holdService.release(bar.getLotNo(), new HoldCommand(QC, "재검사 합격", true));
        assertThat(holdService.heldLots()).extracting(h -> h.lot().getLotNo()).containsExactly(other.getLotNo());
    }

    @Test
    @DisplayName("보류한 LOT은 투입할 수 없다 (409 LOT_ON_HOLD). 실적은 저장되지 않는다")
    void heldLotCannotBeUsed() {
        Lot bar = receive(10);
        holdService.hold(bar.getLotNo(), by(QC, false));
        operationService.start(op10, "W1001");

        expectError(ErrorCode.LOT_ON_HOLD, () -> report(op10, 2, bar.getLotNo(), 2));
        assertThat(lot(bar.getLotNo()).getQty()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("다 쓴 LOT은 건너뛰고 하위 LOT만 보류한다. 하위를 빼면 시작 LOT만 본다")
    void consumedSkipped() {
        Lot bar = receive(5);
        operationService.start(op10, "W1001");
        String turned = outputOf(report(op10, 5, bar.getLotNo(), 5));
        forward(bar.getLotNo(), turned);

        HoldResult only = holdService.hold(bar.getLotNo(), by(QC, false));
        assertThat(only.changed()).isEmpty();
        assertThat(only.skipped()).extracting(Lot::getLotNo, Lot::getStatus)
                .containsExactly(tuple(bar.getLotNo(), LotStatus.CONSUMED));

        HoldResult all = holdService.hold(bar.getLotNo(), by(QC, true));
        assertThat(all.changed()).extracting(Lot::getLotNo).containsExactly(turned);
        assertThat(lotService.detail(bar.getLotNo()).history()).extracting(LotHistory::getEventType)
                .doesNotContain(LotEventType.HOLD);
    }

    @Test
    @DisplayName("작업자(OPERATOR)는 보류·해제할 수 없다 (403 NOT_ALLOWED). 없는 LOT은 404")
    void onlyQualityOrAdmin() {
        Lot bar = receive(10);

        expectError(ErrorCode.NOT_ALLOWED, () -> holdService.hold(bar.getLotNo(), by("W1001", false)));
        expectError(ErrorCode.NOT_ALLOWED, () -> holdService.release(bar.getLotNo(), by("W1001", false)));
        expectError(ErrorCode.LOT_NOT_FOUND, () -> holdService.hold("NO-SUCH-LOT", by(QC, false)));
        assertThat(lot(bar.getLotNo()).getStatus()).isEqualTo(LotStatus.ACTIVE);
    }

    @Test
    @DisplayName("해제: 보류 중인 LOT만 ACTIVE로 돌리고 RELEASE 이력을 남긴다. 해제한 LOT은 다시 투입할 수 있다")
    void release() {
        Lot bar = receive(10);
        Lot other = receive(10);
        forward(bar.getLotNo(), other.getLotNo());   // 보류하지 않은 LOT이 하위에 있으면 건너뛴다
        holdService.hold(bar.getLotNo(), by(QC, false));

        HoldResult result = holdService.release(bar.getLotNo(), new HoldCommand(QC, "재검사 합격", true));

        assertThat(result.changed()).extracting(Lot::getLotNo).containsExactly(bar.getLotNo());
        assertThat(result.skipped()).extracting(Lot::getLotNo).containsExactly(other.getLotNo());
        assertThat(lotService.detail(bar.getLotNo()).history()).extracting(LotHistory::getEventType)
                .containsExactly(LotEventType.RECEIVE, LotEventType.HOLD, LotEventType.RELEASE);

        operationService.start(op10, "W1001");
        report(op10, 2, bar.getLotNo(), 2);
        assertThat(lot(bar.getLotNo()).getQty()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("취소: 보류 LOT을 만든 실적은 취소할 수 없고(LOT_ON_HOLD), 보류 LOT에 투입 수량을 되돌릴 때는 보류를 유지한다")
    void cancelWithHold() {
        Lot bar = receive(10);
        operationService.start(op10, "W1001");
        WorkResult turnedResult = report(op10, 5, bar.getLotNo(), 5);
        String turned = outputOf(turnedResult);
        operationService.start(op20, "W1001");
        WorkResult milled = report(op20, 2, turned, 2);
        holdService.hold(turned, by(QC, false));

        operationService.cancel(milled.getResultId(), "W1001", "수량 오입력");
        assertThat(lot(turned).getStatus()).isEqualTo(LotStatus.HOLD);
        assertThat(lot(turned).getQty()).isEqualByComparingTo("5");

        expectError(ErrorCode.LOT_ON_HOLD, () -> operationService.cancel(turnedResult.getResultId(), "W1001", "수량 오입력"));
    }

    @Test
    @DisplayName("하위 LOT을 잠그는 사이 그 LOT으로 새 LOT이 생기면, 다시 추적해 함께 보류한다")
    void retraceAfterLock() {
        Lot bar = receive(10);
        Lot child = receive(5);   // 하위 LOT이라고 친다
        Lot late = receive(5);    // child를 잠그기 전에 child로 만든 LOT이라고 친다
        when(traceMapper.selectForward(bar.getLotNo()))
                .thenReturn(nodes(child.getLotNo()))
                .thenReturn(nodes(child.getLotNo(), late.getLotNo()));

        HoldResult result = holdService.hold(bar.getLotNo(), by(QC, true));

        assertThat(result.changed()).extracting(Lot::getLotNo)
                .containsExactly(bar.getLotNo(), child.getLotNo(), late.getLotNo());
    }
}
