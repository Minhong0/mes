package com.minimes.monitoring.service;

import com.minimes.monitoring.mapper.DowntimeReasonRow;
import com.minimes.monitoring.mapper.MonitoringMapper;
import com.minimes.monitoring.service.DowntimePareto.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 정지 사유 파레토. 합계와 누계는 SQL(윈도 함수)이 구하고, 여기서는 비율과 "중점 관리 대상" 표시만 붙인다.
 * 정지는 시작한 날짜로 기간에 넣는다. 자정을 넘긴 정지도 시작한 날에 전부 센다.
 */
@Service
@RequiredArgsConstructor
public class DowntimeParetoService {

    /** 기간을 정하지 않으면 가장 최근 정지가 있었던 날까지 30일 */
    static final int DEFAULT_DAYS = 30;

    private final MonitoringMapper monitoringMapper;
    private final Clock clock;

    public DowntimePareto pareto(LocalDate from, LocalDate to, String equipCode, boolean byReason) {
        if (to == null) {
            LocalDate latest = monitoringMapper.selectLatestDowntimeDate();
            to = latest != null ? latest : LocalDate.now(clock);
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1);
        }

        List<DowntimeReasonRow> rows = monitoringMapper.selectDowntimeByReason(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), equipCode, byReason);

        List<Item> items = new ArrayList<>();
        BigDecimal previousCumulative = BigDecimal.ZERO;
        int stopCount = 0;
        for (DowntimeReasonRow row : rows) {
            BigDecimal cumulative = percent(row.cumulativeMin(), row.grandTotalMin());
            items.add(new Item(row.dtCat(), row.dtCatName(), row.dtSub(), row.dtSubName(), row.stopCount(),
                    row.totalMin(), percent(row.totalMin(), row.grandTotalMin()), cumulative,
                    previousCumulative.compareTo(DowntimePareto.VITAL_FEW_PERCENT) < 0));
            previousCumulative = cumulative;
            stopCount += row.stopCount();
        }
        int totalMin = rows.isEmpty() ? 0 : rows.getFirst().grandTotalMin();
        return new DowntimePareto(from, to, equipCode, byReason, totalMin, stopCount, items);
    }

    private static BigDecimal percent(int part, int whole) {
        if (whole == 0) {
            return BigDecimal.ZERO;   // 정지 시간이 모두 0분인 기간
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }
}
