package com.minimes.monitoring.web;

import com.minimes.monitoring.service.Dashboard;
import com.minimes.monitoring.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "모니터링", description = "설비 현황 대시보드 데이터")
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @Operation(summary = "설비 현황", description = "CNC01~05의 현재 상태, 진행 중인 정지 사유, 마지막 센서값, 오늘 OEE (공장 시계 기준)")
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return monitoringService.dashboard();
    }
}
