package com.minimes.monitoring.web;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.monitoring.service.Dashboard;
import com.minimes.monitoring.service.DowntimePareto;
import com.minimes.monitoring.service.DowntimeParetoService;
import com.minimes.monitoring.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "모니터링", description = "설비 현황 대시보드, 정지 사유 파레토")
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final DowntimeParetoService downtimeParetoService;

    @Operation(summary = "설비 현황", description = "CNC01~05의 현재 상태, 진행 중인 정지 사유, 마지막 센서값, 오늘 OEE (공장 시계 기준)")
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return monitoringService.dashboard();
    }

    @Operation(summary = "정지 사유 파레토",
            description = "예: from=2025-10-01&to=2025-10-31 (부트캠프 실적). 기간을 비우면 가장 최근 정지일까지 30일. "
                    + "equipCode를 비우면 전체 설비, byReason=false면 대분류별")
    @GetMapping("/downtime-pareto")
    public DowntimePareto downtimePareto(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(required = false) String equipCode,
                                         @RequestParam(defaultValue = "true") boolean byReason) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from이 to보다 늦습니다.");
        }
        return downtimeParetoService.pareto(from, to, equipCode == null || equipCode.isBlank() ? null : equipCode, byReason);
    }
}
