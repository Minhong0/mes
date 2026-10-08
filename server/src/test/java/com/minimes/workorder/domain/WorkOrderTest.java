package com.minimes.workorder.domain;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.master.domain.Routing;
import com.minimes.master.domain.Routing.TimeBasis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 작업지시 상태 규칙 단위 테스트. Spring과 DB 없이 엔티티만으로 검증한다.
 */
class WorkOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 9, 0);

    private final List<Routing> routings = List.of(
            Routing.of("FG-SFT-100", 10, "OP-10", "WC-100", "SF-SFT-010", new BigDecimal("12"), TimeBasis.EA, null, new BigDecimal("30")),
            Routing.of("FG-SFT-100", 30, "OP-30", "WC-300", "SF-SFT-030", new BigDecimal("480"), TimeBasis.BATCH, 200, BigDecimal.ZERO)
    );

    private WorkOrder newWorkOrder() {
        return WorkOrder.create("WO-261008-001", "FG-SFT-100", 100, LocalDate.of(2026, 10, 10), null, routings, NOW);
    }

    @Test
    @DisplayName("작업지시를 만들면 라우팅 순서대로 공정별 작업이 대기 상태로 생긴다")
    void createCopiesRoutings() {
        WorkOrder wo = newWorkOrder();

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.WAIT);
        assertThat(wo.getOperations())
                .extracting(WorkOrderOperation::getSeq, WorkOrderOperation::getProcessCode, WorkOrderOperation::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10, "OP-10", WorkOrderOperation.Status.WAIT),
                        org.assertj.core.groups.Tuple.tuple(30, "OP-30", WorkOrderOperation.Status.WAIT));
    }

    @Test
    @DisplayName("라우팅이 없으면 작업지시를 만들 수 없다")
    void createWithoutRoutingFails() {
        assertThatThrownBy(() -> WorkOrder.create("WO-261008-001", "RM-SCM-001", 10, null, null, List.of(), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTING_NOT_FOUND);
    }

    @Test
    @DisplayName("대기 → 진행 → 완료 → 마감 순서로 바뀌고 시각이 기록된다")
    void normalFlow() {
        WorkOrder wo = newWorkOrder();

        wo.start(NOW);
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.RUN);
        assertThat(wo.getActualStart()).isEqualTo(NOW);

        wo.complete(NOW.plusHours(8));
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DONE);
        assertThat(wo.getActualEnd()).isEqualTo(NOW.plusHours(8));

        wo.close();
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
    }

    @Test
    @DisplayName("대기 상태에서 바로 완료할 수 없다")
    void cannotCompleteBeforeStart() {
        WorkOrder wo = newWorkOrder();

        assertThatThrownBy(() -> wo.complete(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WAIT 상태의 작업지시는 완료할 수 없습니다");
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.WAIT);   // 실패해도 상태는 그대로
    }

    @Test
    @DisplayName("이미 시작한 작업지시를 다시 시작할 수 없다")
    void cannotStartTwice() {
        WorkOrder wo = newWorkOrder();
        wo.start(NOW);

        assertThatThrownBy(() -> wo.start(NOW.plusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(wo.getActualStart()).isEqualTo(NOW);   // 처음 시작 시각이 덮어써지지 않는다
    }

    @Test
    @DisplayName("공정 목록은 밖에서 수정할 수 없다")
    void operationsAreReadOnly() {
        WorkOrder wo = newWorkOrder();

        assertThatThrownBy(() -> wo.getOperations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
