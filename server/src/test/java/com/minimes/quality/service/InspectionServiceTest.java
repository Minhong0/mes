package com.minimes.quality.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.mapper.LotTraceMapper;
import com.minimes.lot.mapper.TraceNode;
import com.minimes.lot.repository.LotRepository;
import com.minimes.lot.service.LotInput;
import com.minimes.lot.service.LotService;
import com.minimes.lot.service.LotService.ReceiveCommand;
import com.minimes.quality.domain.InspectionResult;
import com.minimes.quality.domain.Judgement;
import com.minimes.quality.repository.InspectionResultRepository;
import com.minimes.quality.service.InspectionService.InspectCommand;
import com.minimes.quality.service.InspectionService.InspectionSheet;
import com.minimes.quality.service.InspectionService.Measurement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 검사: 선삭 LOT을 외경(37.95~38.05)·표면 거칠기(3.2 이하)로 시료 여러 개 재고, 하나라도 벗어나면 LOT을 보류한다.
 * 보류의 하위 LOT 추적(재귀 CTE)은 MSSQL 전용이라 결과를 정해 준다 (LotHoldTest와 같음).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql", "/sql/lot-fixture.sql", "/sql/inspection-fixture.sql"})
class InspectionServiceTest {

    @MockitoBean
    LotTraceMapper traceMapper;

    @Autowired
    InspectionService inspectionService;

    @Autowired
    LotService lotService;

    @Autowired
    OperationService operationService;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    LotRepository lotRepository;

    @Autowired
    InspectionResultRepository resultRepository;

    Lot bar;
    String turned;   // 선삭(OP-10)에서 만든 LOT

    @BeforeEach
    void turnOneLot() {
        when(traceMapper.selectForward(anyString())).thenReturn(List.of());
        WorkOrder wo = workOrderService.create("FG-SFT-100", 100, null, null);
        long op10 = wo.getOperations().getFirst().getWoOpId();
        bar = lotService.receive(new ReceiveCommand("RM-SCM-001", BigDecimal.TEN, null, "W1001"));
        operationService.start(op10, "W1001");
        WorkResult result = operationService.report(op10, new ReportCommand(UUID.randomUUID().toString(), "W1001", null, 5,
                List.of(), List.of(new LotInput(bar.getLotNo(), BigDecimal.valueOf(5)))));
        turned = lotService.resultLots(List.of(result.getResultId())).get(result.getResultId()).outputLotNos().getFirst();
    }

    /** 외경 값들과 표면 거칠기 값들. 시료 번호는 1번부터 순서대로 */
    static InspectCommand values(String[] od, String[] ra) {
        List<Measurement> list = new ArrayList<>();
        for (int i = 0; i < od.length; i++) {
            list.add(new Measurement("OD", i + 1, new BigDecimal(od[i])));
        }
        for (int i = 0; i < ra.length; i++) {
            list.add(new Measurement("RA", i + 1, new BigDecimal(ra[i])));
        }
        return new InspectCommand("W1001", list);
    }

    static String[] of(String... values) {
        return values;
    }

    void expectError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    List<LotHistory> history(String lotNo) {
        return lotService.detail(lotNo).history();
    }

    @Test
    @DisplayName("검사표: LOT을 만든 공정(선삭)의 항목과 하한·상한. 검사 전이라 판정이 없다")
    void sheetBeforeInspection() {
        InspectionSheet sheet = inspectionService.sheet(turned);

        assertThat(sheet.processName()).isEqualTo("CNC 선삭");
        assertThat(sheet.specs()).extracting(s -> s.getInspectCode(), s -> s.getUsl())
                .containsExactly(tuple("OD", new BigDecimal("38.0500")), tuple("RA", new BigDecimal("3.2000")));
        assertThat(sheet.judgement()).isNull();
        assertThat(sheet.sampleCount()).isZero();
    }

    @Test
    @DisplayName("합격: 시료 3개 모두 범위 안 (경계값 포함, 상한만 있는 항목은 하한을 보지 않음). LOT은 그대로 ACTIVE")
    void pass() {
        InspectionSheet sheet = inspectionService.inspect(turned,
                values(of("37.95", "38.00", "38.05"), of("0.4", "3.2", "1.1")));

        assertThat(sheet.judgement()).isEqualTo(Judgement.OK);
        assertThat(sheet.sampleCount()).isEqualTo(3);
        assertThat(sheet.results()).hasSize(6).allMatch(r -> r.getJudgement() == Judgement.OK);
        assertThat(sheet.hold()).isNull();
        assertThat(lotRepository.findById(turned).orElseThrow().getStatus()).isEqualTo(LotStatus.ACTIVE);
        assertThat(history(turned).getLast()).extracting(LotHistory::getEventType, LotHistory::getNote, LotHistory::getProcessCode)
                .containsExactly(LotEventType.INSPECT, "합격 (시료 3개, 측정 6개)", "OP-10");
    }

    @Test
    @DisplayName("불합격: 외경 시료 2가 상한을 넘으면 그 값만 NG, LOT은 NG이고 바로 보류된다 (하위 LOT까지, 작업자가 등록해도)")
    void failHolds() {
        Lot downstream = lotService.receive(new ReceiveCommand("RM-SCM-001", BigDecimal.ONE, null, "W1001"));   // 하위 LOT이라고 친다
        when(traceMapper.selectForward(turned)).thenReturn(List.of(
                new TraceNode(1, turned, downstream.getLotNo(), null, null, null, null, null, BigDecimal.ONE, null, null, null, null)));

        InspectionSheet sheet = inspectionService.inspect(turned, values(of("38.00", "38.07"), of("1.0", "1.2")));

        assertThat(sheet.judgement()).isEqualTo(Judgement.NG);
        assertThat(sheet.results()).filteredOn(r -> r.getJudgement() == Judgement.NG)
                .extracting(InspectionResult::getInspectCode, InspectionResult::getSampleNo).containsExactly(tuple("OD", 2));
        assertThat(sheet.hold().changed()).extracting(Lot::getLotNo).containsExactly(turned, downstream.getLotNo());
        assertThat(sheet.lot().getStatus()).isEqualTo(LotStatus.HOLD);
        assertThat(history(turned)).extracting(LotHistory::getEventType, LotHistory::getNote).endsWith(
                tuple(LotEventType.INSPECT, "불합격: 외경 #2 38.07 (상한 38.05)"),
                tuple(LotEventType.HOLD, "검사 불합격: 외경 #2 38.07 (상한 38.05)"));
    }

    @Test
    @DisplayName("NG가 여러 개면 첫 건과 나머지 수를 적는다. 하한 아래도 NG")
    void manyNg() {
        InspectionSheet sheet = inspectionService.inspect(turned, values(of("37.90", "38.10"), of("3.3", "1.0")));

        assertThat(sheet.results()).filteredOn(r -> r.getJudgement() == Judgement.NG).hasSize(3);
        assertThat(history(turned)).extracting(LotHistory::getNote).contains("불합격: 외경 #1 37.9 (하한 37.95) 외 2건");
    }

    @Test
    @DisplayName("빠진 측정값, 다른 공정 항목, 시료 번호 범위 밖, 같은 시료 두 번이면 저장하지 않는다")
    void incomplete() {
        expectError(ErrorCode.INVALID_INPUT, () -> inspectionService.inspect(turned, values(of("38.0", "38.0"), of("1.0"))));
        expectError(ErrorCode.INVALID_INPUT, () -> inspectionService.inspect(turned, new InspectCommand("W1001", List.of(
                new Measurement("OD", 1, BigDecimal.ONE), new Measurement("RA", 1, BigDecimal.ONE),
                new Measurement("KW", 1, BigDecimal.ONE)))));
        expectError(ErrorCode.INVALID_INPUT, () -> inspectionService.inspect(turned, new InspectCommand("W1001", List.of(
                new Measurement("OD", 11, BigDecimal.ONE), new Measurement("RA", 11, BigDecimal.ONE)))));
        expectError(ErrorCode.INVALID_INPUT, () -> inspectionService.inspect(turned, new InspectCommand("W1001", List.of(
                new Measurement("OD", 1, BigDecimal.ONE), new Measurement("OD", 1, BigDecimal.TWO),
                new Measurement("RA", 1, BigDecimal.ONE)))));
        expectError(ErrorCode.WORKER_NOT_FOUND, () -> inspectionService.inspect(turned,
                new InspectCommand("W1099", List.of(new Measurement("OD", 1, BigDecimal.ONE)))));

        assertThat(resultRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("같은 값을 다시 보내면 처음 결과를 돌려주고 두 번 저장하지 않는다. 다른 값이면 409 INSPECTION_DONE")
    void resend() {
        InspectCommand command = values(of("38.0", "38.07"), of("1.0", "1.0"));
        inspectionService.inspect(turned, command);

        InspectionSheet again = inspectionService.inspect(turned, values(of("38.000", "38.07"), of("1", "1.0")));

        assertThat(again.judgement()).isEqualTo(Judgement.NG);
        assertThat(resultRepository.findAll()).hasSize(4);
        assertThat(history(turned)).extracting(LotHistory::getEventType)
                .filteredOn(e -> e == LotEventType.INSPECT || e == LotEventType.HOLD).hasSize(2);
        expectError(ErrorCode.INSPECTION_DONE, () -> inspectionService.inspect(turned, values(of("38.0", "38.0"), of("1.0", "1.0"))));
    }

    @Test
    @DisplayName("입고한 원자재 LOT은 검사 항목이 없다 (검사표가 비고, 등록은 400). 없는 LOT은 404")
    void rawMaterialHasNoSpecs() {
        assertThat(inspectionService.sheet(bar.getLotNo()).specs()).isEmpty();
        expectError(ErrorCode.INVALID_INPUT, () -> inspectionService.inspect(bar.getLotNo(), values(of("1"), of("1"))));
        expectError(ErrorCode.LOT_NOT_FOUND, () -> inspectionService.sheet("NO-SUCH-LOT"));
    }
}
