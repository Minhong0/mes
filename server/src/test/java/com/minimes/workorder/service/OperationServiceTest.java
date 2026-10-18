package com.minimes.workorder.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderOperation;
import com.minimes.workorder.domain.WorkOrderStatus;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.domain.WorkResult.DefectLine;
import com.minimes.workorder.mapper.OperationItem;
import com.minimes.workorder.repository.WorkResultRepository;
import com.minimes.workorder.service.OperationService.ReportCommand;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * POP 흐름: 작업지시(공정 3개: WC-100 → WC-200 → WC-300)를 만들고 시작 → 실적 → 완료 → 취소를 해 본다.
 * 이 테스트의 기준정보에는 BOM이 없어 첫 공정에 투입할 LOT이 없다. LOT 투입·산출은 LotFlowTest가 본다.
 * 시계는 테스트가 앞으로 돌릴 수 있게 만들어, 실적마다 시각이 다르게 남는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql"})
class OperationServiceTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 17, 9, 0);

    /** 테스트가 시각을 정할 수 있는 시계 */
    static class TestClock extends Clock {
        Instant now = T0.atZone(ZONE).toInstant();

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    TestClock clock;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    OperationService operationService;

    @Autowired
    WorkResultRepository resultRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ApplicationEvents events;

    WorkOrder wo;
    long op10;
    long op20;

    @BeforeEach
    void createWorkOrder() {
        clock.now = T0.atZone(ZONE).toInstant();
        wo = workOrderService.create("FG-SFT-100", 100, null, null);
        op10 = wo.getOperations().get(0).getWoOpId();
        op20 = wo.getOperations().get(1).getWoOpId();
    }

    ReportCommand report(int good, DefectLine... defects) {
        return new ReportCommand(UUID.randomUUID().toString(), "W1001", "CNC01", good, List.of(defects), List.of());
    }

    void expectError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("첫 공정을 시작하면 작업지시도 진행(RUN)이 되고 시작 시각이 남는다")
    void startFirstOperation() {
        WorkOrderOperation op = operationService.start(op10, "W1001");

        assertThat(op.getStatus()).isEqualTo(WorkOrderOperation.Status.RUN);
        assertThat(op.getStartedAt()).isEqualTo(T0);
        assertThat(op.getWorkOrder().getStatus()).isEqualTo(WorkOrderStatus.RUN);
        assertThat(op.getWorkOrder().getActualStart()).isEqualTo(T0);
    }

    @Test
    @DisplayName("실적은 공정 합계에 더해지고, 실적 구간은 앞 실적이 끝난 때부터 이어진다")
    void reportAccumulates() {
        operationService.start(op10, "W1001");
        clock.advance(Duration.ofMinutes(60));
        WorkResult first = operationService.report(op10, report(5, new DefectLine("OVERSIZE", 1)));
        clock.advance(Duration.ofMinutes(30));
        WorkResult second = operationService.report(op10, report(3,
                new DefectLine("OVERSIZE", 1), new DefectLine("SCRATCH", 2)));

        assertThat(first.getStartedAt()).isEqualTo(T0);
        assertThat(first.getEndedAt()).isEqualTo(T0.plusMinutes(60));
        assertThat(second.getStartedAt()).isEqualTo(T0.plusMinutes(60));
        assertThat(second.getDefectQty()).isEqualTo(3);
        assertThat(second.getDefects()).extracting("defectType", "qty")
                .containsExactly(tuple("OVERSIZE", 1), tuple("SCRATCH", 2));

        assertThat(wo.getOperations().getFirst().getGoodQty()).isEqualTo(8);
        assertThat(wo.getOperations().getFirst().getDefectQty()).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 requestId로 다시 보내면 새로 저장하지 않고 처음 실적을 돌려준다 (합계도 한 번만)")
    void sameRequestOnce() {
        operationService.start(op10, "W1001");
        ReportCommand command = report(10);

        WorkResult first = operationService.report(op10, command);
        WorkResult retry = operationService.report(op10, command);

        assertThat(retry.getResultId()).isEqualTo(first.getResultId());
        assertThat(resultRepository.findAll()).hasSize(1);
        assertThat(wo.getOperations().getFirst().getGoodQty()).isEqualTo(10);
    }

    @Test
    @DisplayName("시작하지 않은 공정에는 실적을 등록할 수 없다")
    void reportBeforeStart() {
        expectError(ErrorCode.INVALID_STATUS_TRANSITION, () -> operationService.report(op10, report(1)));
    }

    @Test
    @DisplayName("입력 확인: 수량 0, 모르는 불량 유형, 다른 작업장 설비, 없는·퇴사한 작업자")
    void validation() {
        operationService.start(op10, "W1001");

        expectError(ErrorCode.INVALID_INPUT, () -> operationService.report(op10, report(0)));
        expectError(ErrorCode.INVALID_INPUT, () -> operationService.report(op10, report(1, new DefectLine("BURR", 1))));
        expectError(ErrorCode.INVALID_INPUT, () -> operationService.report(op10,
                new ReportCommand(UUID.randomUUID().toString(), "W1001", "CNC03", 1, List.of(), List.of())));   // CNC03은 WC-200
        expectError(ErrorCode.WORKER_NOT_FOUND, () -> operationService.report(op10,
                new ReportCommand(UUID.randomUUID().toString(), "W0000", "CNC01", 1, List.of(), List.of())));
        expectError(ErrorCode.WORKER_NOT_FOUND, () -> operationService.start(op20, "W1099"));
    }

    @Test
    @DisplayName("실적을 취소하면 합계에서 빠지고, 두 번 취소할 수는 없다")
    void cancel() {
        operationService.start(op10, "W1001");
        WorkResult kept = operationService.report(op10, report(5));
        WorkResult wrong = operationService.report(op10, report(50, new DefectLine("SCRATCH", 2)));

        WorkResult cancelled = operationService.cancel(wrong.getResultId(), "W1001", "수량 오입력");

        assertThat(cancelled.isCancelled()).isTrue();
        assertThat(cancelled.getCancelReason()).isEqualTo("수량 오입력");
        assertThat(wo.getOperations().getFirst().getGoodQty()).isEqualTo(5);
        assertThat(wo.getOperations().getFirst().getDefectQty()).isZero();
        assertThat(kept.isCancelled()).isFalse();
        expectError(ErrorCode.INVALID_STATUS_TRANSITION, () -> operationService.cancel(wrong.getResultId(), "W1001", "다시"));
    }

    @Test
    @DisplayName("공정을 모두 완료해야 작업지시가 완료(DONE)된다")
    void completeAll() {
        for (WorkOrderOperation op : wo.getOperations()) {
            operationService.start(op.getWoOpId(), "W1001");
            operationService.complete(op.getWoOpId(), "W1001");
            if (op.getSeq() < 30) {
                assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.RUN);
            }
        }
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DONE);

        // 완료된 작업지시에는 실적을 더 받지 않는다
        expectError(ErrorCode.INVALID_STATUS_TRANSITION, () -> operationService.report(op10, report(1)));
    }

    @Test
    @DisplayName("작업 목록: 작업장별로, 진행 중인 것이 먼저, 앞 공정 양품 수량과 함께")
    void openOperations() {
        WorkOrder second = workOrderService.create("FG-SFT-100", 30, null, null);
        operationService.start(second.getOperations().getFirst().getWoOpId(), "W1001");   // 두 번째 작업지시를 먼저 시작
        operationService.report(second.getOperations().getFirst().getWoOpId(), report(12));
        // JPA는 바뀐 값을 커밋(또는 flush) 때 DB에 쓴다. 작업 목록은 MyBatis가 DB에서 직접 읽으므로 먼저 써 둔다.
        // (실제 API는 요청마다 트랜잭션이 커밋되므로 이 문제가 없다. 한 트랜잭션 안에서 섞어 쓰는 테스트에서만 필요하다)
        entityManager.flush();

        List<OperationItem> wc100 = operationService.openOperations("WC-100");
        List<OperationItem> wc200 = operationService.openOperations("WC-200");

        assertThat(wc100).extracting(OperationItem::woNo, OperationItem::status)
                .containsExactly(tuple(second.getWoNo(), "RUN"), tuple(wo.getWoNo(), "WAIT"));
        assertThat(wc100.getFirst().prevGoodQty()).isNull();   // 첫 공정
        assertThat(wc100.getFirst().processName()).isEqualTo("CNC 선삭");
        assertThat(wc200).extracting(OperationItem::woNo, OperationItem::prevGoodQty)
                .containsExactlyInAnyOrder(tuple(second.getWoNo(), 12), tuple(wo.getWoNo(), 0));
    }

    @Test
    @DisplayName("실적을 등록·취소하면 그날 집계를 다시 하라고 알린다. 설비를 비운 실적, 다시 보낸 요청은 알리지 않는다")
    void resultChangedEvent() {
        operationService.start(op10, "W1001");
        ReportCommand command = report(5, new DefectLine("OVERSIZE", 1));
        WorkResult result = operationService.report(op10, command);
        operationService.report(op10, command);   // 다시 보낸 요청
        operationService.report(op10, new ReportCommand(UUID.randomUUID().toString(), "W1001", null, 2, List.of(), List.of()));
        operationService.cancel(result.getResultId(), "W1001", "수량 오입력");

        assertThat(events.stream(WorkResultChangedEvent.class))
                .containsExactly(new WorkResultChangedEvent("CNC01", T0.toLocalDate()),
                        new WorkResultChangedEvent("CNC01", T0.toLocalDate()));
    }
}
