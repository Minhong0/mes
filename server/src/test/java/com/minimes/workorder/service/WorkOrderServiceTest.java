package com.minimes.workorder.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderStatus;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서비스 + JPA + H2 테스트. 테스트마다 롤백한다.
 * 시각은 2026-10-08 09:00으로 고정해서 작업지시 번호를 예측할 수 있게 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/work-order-fixture.sql")
class WorkOrderServiceTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            return Clock.fixed(LocalDateTime.of(2026, 10, 8, 9, 0).atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    WorkOrderService workOrderService;

    @Test
    @DisplayName("작업지시 번호는 날짜별 일련번호로 매겨진다")
    void numbering() {
        WorkOrder first = workOrderService.create("FG-SFT-100", 100, null, null);
        WorkOrder second = workOrderService.create("FG-SFT-100", 50, null, null);

        assertThat(first.getWoNo()).isEqualTo("WO-261008-001");
        assertThat(second.getWoNo()).isEqualTo("WO-261008-002");
    }

    @Test
    @DisplayName("생성한 작업지시를 다시 조회하면 라우팅 3개가 공정별 작업으로 저장되어 있다")
    void createAndGet() {
        String woNo = workOrderService.create("FG-SFT-100", 100, null, "테스트").getWoNo();

        WorkOrder found = workOrderService.get(woNo);

        assertThat(found.getStatus()).isEqualTo(WorkOrderStatus.WAIT);
        assertThat(found.getOperations()).hasSize(3);
        assertThat(found.getOperations().get(0).getWoOpId()).isNotNull();   // IDENTITY 컬럼이 번호를 매겼다
    }

    @Test
    @DisplayName("상태 변경이 저장된다 (save를 부르지 않아도 변경 감지로 UPDATE)")
    void statusChangesArePersisted() {
        String woNo = workOrderService.create("FG-SFT-100", 100, null, null).getWoNo();

        workOrderService.start(woNo);
        workOrderService.complete(woNo);

        WorkOrder found = workOrderService.get(woNo);
        assertThat(found.getStatus()).isEqualTo(WorkOrderStatus.DONE);
        assertThat(found.getActualStart()).isEqualTo(LocalDateTime.of(2026, 10, 8, 9, 0));
    }

    @Test
    @DisplayName("없는 품목으로는 작업지시를 만들 수 없다")
    void unknownItem() {
        assertThatThrownBy(() -> workOrderService.create("NO-SUCH-ITEM", 10, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("라우팅이 없는 품목(원자재)으로는 작업지시를 만들 수 없다")
    void itemWithoutRouting() {
        assertThatThrownBy(() -> workOrderService.create("RM-SCM-001", 10, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTING_NOT_FOUND);
    }
}
