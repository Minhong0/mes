package com.minimes.lot.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotGenealogy;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.repository.LotGenealogyRepository;
import com.minimes.lot.repository.LotRepository;
import com.minimes.lot.service.LotService.InputCandidates;
import com.minimes.lot.service.LotService.ReceiveCommand;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.domain.WorkResult.DefectLine;
import com.minimes.workorder.repository.WorkResultRepository;
import com.minimes.workorder.service.OperationService;
import com.minimes.workorder.service.OperationService.ReportCommand;
import com.minimes.workorder.service.WorkOrderService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * LOT 흐름: 환봉 입고 → 선삭 실적(환봉 투입, 1차 가공 LOT 생성) → 밀링 → 열처리(LOT 여러 개를 한 Batch로) → 취소.
 * 공정 3개(OP-10 → OP-20 → OP-30)와 BOM은 work-order-fixture.sql, lot-fixture.sql에 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql", "/sql/lot-fixture.sql"})
class LotFlowTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 17, 9, 0);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            return Clock.fixed(T0.atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    LotService lotService;

    @Autowired
    OperationService operationService;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    LotRepository lotRepository;

    @Autowired
    LotGenealogyRepository genealogyRepository;

    @Autowired
    WorkResultRepository resultRepository;

    WorkOrder wo;
    long op10;
    long op20;
    long op30;

    @BeforeEach
    void createWorkOrder() {
        wo = workOrderService.create("FG-SFT-100", 100, null, null);
        op10 = wo.getOperations().get(0).getWoOpId();
        op20 = wo.getOperations().get(1).getWoOpId();
        op30 = wo.getOperations().get(2).getWoOpId();
    }

    Lot receive(int qty) {
        return lotService.receive(new ReceiveCommand("RM-SCM-001", BigDecimal.valueOf(qty), "SUP-A-1017", "W1001"));
    }

    WorkResult report(long woOpId, int good, int defect, LotInput... inputs) {
        List<DefectLine> defects = defect == 0 ? List.of() : List.of(new DefectLine("OVERSIZE", defect));
        return operationService.report(woOpId,
                new ReportCommand(UUID.randomUUID().toString(), "W1001", null, good, defects, List.of(inputs)));
    }

    static LotInput in(Lot lot, int qty) {
        return in(lot.getLotNo(), qty);
    }

    static LotInput in(String lotNo, int qty) {
        return new LotInput(lotNo, BigDecimal.valueOf(qty));
    }

    String outputOf(WorkResult result) {
        return lotService.resultLots(List.of(result.getResultId())).get(result.getResultId()).outputLotNo();
    }

    Lot lot(String lotNo) {
        return lotRepository.findById(lotNo).orElseThrow();
    }

    void expectError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("입고: LOT 번호는 품번-yyMMdd-일련번호, LOT 관리 원자재만 받는다")
    void receiveMaterial() {
        Lot first = receive(100);
        Lot second = receive(50);

        assertThat(first.getLotNo()).isEqualTo("RM-SCM-001-261017-001");
        assertThat(second.getLotNo()).isEqualTo("RM-SCM-001-261017-002");
        assertThat(first.getStatus()).isEqualTo(LotStatus.ACTIVE);
        assertThat(first.getSupplierLotNo()).isEqualTo("SUP-A-1017");
        assertThat(lotService.detail(first.getLotNo()).history()).extracting(LotHistory::getEventType)
                .containsExactly(LotEventType.RECEIVE);

        // 절삭유는 LOT 관리를 하지 않고, 반제품은 입고가 아니라 실적으로 만든다
        expectError(ErrorCode.INVALID_INPUT, () -> lotService.receive(
                new ReceiveCommand("RM-OIL-001", BigDecimal.TEN, null, "W1001")));
        expectError(ErrorCode.INVALID_INPUT, () -> lotService.receive(
                new ReceiveCommand("SF-SFT-010", BigDecimal.TEN, null, "W1001")));
    }

    @Test
    @DisplayName("선삭 실적: 환봉 LOT 두 개에서 7개(양품 6 + 불량 1)를 투입하고, 양품 6개로 1차 가공 LOT을 만든다")
    void reportConsumesAndProduces() {
        Lot a = receive(5);
        Lot b = receive(10);
        operationService.start(op10, "W1001");

        WorkResult result = report(op10, 6, 1, in(a, 5), in(b, 2));

        assertThat(lot(a.getLotNo()).getQty()).isEqualByComparingTo("0");
        assertThat(lot(a.getLotNo()).getStatus()).isEqualTo(LotStatus.CONSUMED);
        assertThat(lot(b.getLotNo()).getQty()).isEqualByComparingTo("8");

        Lot output = lot(outputOf(result));
        assertThat(output.getLotNo()).isEqualTo("SF-SFT-010-261017-001");
        assertThat(output.getQty()).isEqualByComparingTo("6");
        assertThat(output.getWoNo()).isEqualTo(wo.getWoNo());
        assertThat(output.getLastProcess()).isEqualTo("OP-10");

        assertThat(genealogyRepository.findByResultId(result.getResultId()))
                .extracting(LotGenealogy::getInputLotNo, LotGenealogy::getOutputLotNo, g -> g.getQty().intValue())
                .containsExactlyInAnyOrder(tuple(a.getLotNo(), output.getLotNo(), 5), tuple(b.getLotNo(), output.getLotNo(), 2));
        assertThat(lotService.resultLots(List.of(result.getResultId())).get(result.getResultId()).inputs())
                .extracting(LotInput::lotNo).containsExactly(a.getLotNo(), b.getLotNo());
    }

    @Test
    @DisplayName("투입 확인: 수량 합이 다르거나, 없는·다른 품목·남은 수량보다 많은·같은 LOT 두 번이면 실적을 저장하지 않는다")
    void inputValidation() {
        Lot a = receive(5);
        operationService.start(op10, "W1001");

        expectError(ErrorCode.INVALID_INPUT, () -> report(op10, 3, 0));                       // 투입 없음
        expectError(ErrorCode.INVALID_INPUT, () -> report(op10, 3, 0, in(a, 2)));             // 3개 필요
        expectError(ErrorCode.LOT_NOT_FOUND, () -> report(op10, 3, 0, in("RM-NONE", 3)));
        expectError(ErrorCode.INVALID_INPUT, () -> report(op10, 6, 0, in(a, 6)));             // 남은 수량 5
        expectError(ErrorCode.INVALID_INPUT, () -> report(op10, 2, 0, in(a, 1), in(a, 1)));   // 같은 LOT 두 번

        assertThat(resultRepository.findAll()).isEmpty();
        assertThat(lot(a.getLotNo()).getQty()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("밀링은 같은 작업지시의 선삭 LOT만 받는다 (환봉 LOT, 다른 작업지시의 선삭 LOT은 안 된다)")
    void nextOperationTakesOwnLots() {
        Lot rm = receive(20);
        operationService.start(op10, "W1001");
        String mine = outputOf(report(op10, 5, 0, in(rm, 5)));

        WorkOrder other = workOrderService.create("FG-SFT-100", 10, null, null);
        long otherOp10 = other.getOperations().getFirst().getWoOpId();
        operationService.start(otherOp10, "W1001");
        String others = outputOf(report(otherOp10, 5, 0, in(rm, 5)));

        operationService.start(op20, "W1001");
        expectError(ErrorCode.INVALID_INPUT, () -> report(op20, 2, 0, in(rm, 2)));
        expectError(ErrorCode.INVALID_INPUT, () -> report(op20, 2, 0, in(others, 2)));

        List<InputCandidates> candidates = lotService.inputCandidates(wo.getOperations().get(1));
        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.required().itemCode()).isEqualTo("SF-SFT-010");
            assertThat(c.required().fromProcess()).isEqualTo("OP-10");
            assertThat(c.lots()).extracting(Lot::getLotNo).containsExactly(mine);
        });

        WorkResult milled = report(op20, 4, 1, in(mine, 5));
        assertThat(outputOf(milled)).isEqualTo("SF-SFT-020-261017-001");
        assertThat(lot(mine).getStatus()).isEqualTo(LotStatus.CONSUMED);
    }

    @Test
    @DisplayName("투입 후보: 선입선출 순서, 다 쓴 LOT은 빠진다. 소모품(절삭유)은 고르지 않는다")
    void candidatesFifo() {
        Lot a = receive(3);
        Lot b = receive(10);
        operationService.start(op10, "W1001");
        report(op10, 3, 0, in(a, 3));

        List<InputCandidates> candidates = lotService.inputCandidates(wo.getOperations().getFirst());

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.required().itemCode()).isEqualTo("RM-SCM-001");
            assertThat(c.required().fromProcess()).isNull();
            assertThat(c.itemName()).isEqualTo("SCM440 환봉");
            assertThat(c.lots()).extracting(Lot::getLotNo).containsExactly(b.getLotNo());
        });
    }

    @Test
    @DisplayName("열처리: 밀링 LOT 두 개를 한 Batch LOT으로 합친다 (계보 2줄)")
    void heatTreatMerges() {
        Lot rm = receive(20);
        operationService.start(op10, "W1001");
        String t1 = outputOf(report(op10, 5, 0, in(rm, 5)));
        String t2 = outputOf(report(op10, 5, 0, in(rm, 5)));
        operationService.start(op20, "W1001");
        String m1 = outputOf(report(op20, 5, 0, in(t1, 5)));
        String m2 = outputOf(report(op20, 5, 0, in(t2, 5)));
        operationService.start(op30, "W1001");

        WorkResult batch = report(op30, 9, 1, in(m1, 5), in(m2, 5));

        assertThat(outputOf(batch)).isEqualTo("SF-SFT-030-261017-001");
        assertThat(genealogyRepository.findByResultId(batch.getResultId()))
                .extracting(LotGenealogy::getInputLotNo).containsExactlyInAnyOrder(m1, m2);
    }

    @Test
    @DisplayName("만든 LOT을 다음 공정에 썼으면 그 실적은 취소할 수 없다 (LOT_IN_USE)")
    void cancelBlockedWhenUsed() {
        Lot rm = receive(10);
        operationService.start(op10, "W1001");
        WorkResult turned = report(op10, 4, 1, in(rm, 5));
        operationService.start(op20, "W1001");
        report(op20, 2, 0, in(outputOf(turned), 2));

        // 실제 요청이라면 트랜잭션이 통째로 롤백되어 아무것도 바뀌지 않는다.
        // 이 테스트는 트랜잭션 하나를 끝까지 쓰므로, 실패한 뒤의 상태는 보지 않는다.
        expectError(ErrorCode.LOT_IN_USE, () -> operationService.cancel(turned.getResultId(), "W1001", "수량 오입력"));
    }

    @Test
    @DisplayName("실적 취소: 만든 LOT은 폐기, 투입 LOT은 수량을 되돌린다. 뒤 공정부터 취소하면 앞 공정도 취소할 수 있다")
    void cancelRestores() {
        Lot rm = receive(10);
        operationService.start(op10, "W1001");
        WorkResult turned = report(op10, 4, 1, in(rm, 5));
        String turnedLot = outputOf(turned);
        operationService.start(op20, "W1001");
        WorkResult milled = report(op20, 2, 0, in(turnedLot, 2));

        operationService.cancel(milled.getResultId(), "W1001", "수량 오입력");
        assertThat(lot(outputOf(milled)).getStatus()).isEqualTo(LotStatus.SCRAPPED);
        assertThat(lot(turnedLot).getQty()).isEqualByComparingTo("4");

        operationService.cancel(turned.getResultId(), "W1001", "수량 오입력");
        assertThat(lot(turnedLot).getStatus()).isEqualTo(LotStatus.SCRAPPED);
        assertThat(lot(turnedLot).getQty()).isEqualByComparingTo("0");
        assertThat(lot(rm.getLotNo()).getQty()).isEqualByComparingTo("10");
        assertThat(lot(rm.getLotNo()).getStatus()).isEqualTo(LotStatus.ACTIVE);
        assertThat(lotService.detail(rm.getLotNo()).history()).extracting(LotHistory::getEventType)
                .containsExactly(LotEventType.RECEIVE, LotEventType.CONSUME, LotEventType.CANCEL);
    }

    @Test
    @DisplayName("모두 불량: 투입만 하고 만든 LOT은 없다. 취소하면 투입 수량이 돌아온다")
    void allDefects() {
        Lot rm = receive(5);
        operationService.start(op10, "W1001");

        WorkResult result = report(op10, 0, 2, in(rm, 2));

        assertThat(lotService.resultLots(List.of(result.getResultId())).get(result.getResultId()))
                .satisfies(l -> {
                    assertThat(l.outputLotNo()).isNull();
                    assertThat(l.inputs()).extracting(LotInput::lotNo).containsExactly(rm.getLotNo());
                });
        assertThat(lot(rm.getLotNo()).getQty()).isEqualByComparingTo("3");

        operationService.cancel(result.getResultId(), "W1001", "불량 오입력");
        assertThat(lot(rm.getLotNo()).getQty()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("같은 요청을 다시 보내면 LOT도 한 번만 줄고 한 번만 만든다")
    void sameRequestOnce() {
        Lot rm = receive(10);
        operationService.start(op10, "W1001");
        ReportCommand command = new ReportCommand("lot-req-1", "W1001", null, 3, List.of(), List.of(in(rm, 3)));

        WorkResult first = operationService.report(op10, command);
        WorkResult retry = operationService.report(op10, command);

        assertThat(retry.getResultId()).isEqualTo(first.getResultId());
        assertThat(lot(rm.getLotNo()).getQty()).isEqualByComparingTo("7");
        assertThat(lotRepository.findAll()).extracting(Lot::getItemCode)
                .containsExactlyInAnyOrder("RM-SCM-001", "SF-SFT-010");
    }
}
