package com.minimes.production.service;

import com.minimes.workorder.service.WorkResultChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 스케줄러가 "새 설비 데이터가 온 날짜"와 "POP 실적이 바뀐 날짜"를 함께 다시 집계하는지 본다. */
class ProductionAggregationSchedulerTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 17);

    ProductionAggregationService service = mock(ProductionAggregationService.class);
    ProductionAggregationScheduler scheduler = new ProductionAggregationScheduler(service, Clock.systemDefaultZone());

    @Test
    @DisplayName("POP 실적이 바뀐 날은 새 설비 데이터가 없어도 다시 집계하고, 한 번 집계하면 목록에서 빠진다")
    void resultChangedDate() {
        when(service.datesReceivedSince(any())).thenReturn(List.of());
        scheduler.onWorkResultChanged(new WorkResultChangedEvent("CNC01", DAY));
        scheduler.onWorkResultChanged(new WorkResultChangedEvent("CNC02", DAY));   // 같은 날짜는 한 번만

        scheduler.run();
        scheduler.run();

        verify(service, times(1)).aggregate(DAY);
    }

    @Test
    @DisplayName("두 목록에 같은 날짜가 있으면 한 번만 집계한다")
    void mergeDates() {
        when(service.datesReceivedSince(any())).thenReturn(List.of(DAY, DAY.plusDays(1)));
        scheduler.onWorkResultChanged(new WorkResultChangedEvent("CNC01", DAY));

        scheduler.run();

        verify(service, times(1)).aggregate(DAY);
        verify(service, times(1)).aggregate(DAY.plusDays(1));
    }

    @Test
    @DisplayName("집계가 실패하면 그 날짜를 버리지 않고 다음 실행에서 다시 한다")
    void retryAfterFailure() {
        when(service.datesReceivedSince(any())).thenReturn(List.of());
        when(service.aggregate(DAY)).thenThrow(new IllegalStateException("DB 오류")).thenReturn(1);
        scheduler.onWorkResultChanged(new WorkResultChangedEvent("CNC01", DAY));

        assertThatThrownBy(scheduler::run).isInstanceOf(IllegalStateException.class);
        scheduler.run();

        verify(service, times(2)).aggregate(DAY);
        verify(service, never()).aggregate(DAY.plusDays(1));
    }
}
