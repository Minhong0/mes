package com.minimes.production.web;

import com.minimes.production.mapper.OeeDaily;
import com.minimes.production.mapper.OeeMapper;
import com.minimes.production.mapper.OeeMonthly;
import com.minimes.production.service.ProductionAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "생산 실적·OEE", description = "설비별 일·월 OEE 조회, 일별 집계 다시 하기")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OeeController {

    private final OeeMapper oeeMapper;
    private final ProductionAggregationService aggregationService;

    @Operation(summary = "일별 OEE", description = "예: date=2025-10-01 (부트캠프 실적), 시뮬레이터를 돌린 날짜 (MES 집계)")
    @GetMapping("/oee/daily")
    public List<OeeDaily> daily(@RequestParam LocalDate date) {
        return oeeMapper.selectDaily(date);
    }

    @Operation(summary = "월별 OEE", description = "예: month=2025-10. 일별 OEE의 평균이 아니라 시간과 수량을 합산해 계산")
    @GetMapping("/oee/monthly")
    public List<OeeMonthly> monthly(@RequestParam YearMonth month) {
        return oeeMapper.selectMonthly(month.atDay(1));
    }

    @Operation(summary = "일별 집계 다시 하기", description = "스케줄러를 기다리지 않고 그날의 수집 데이터로 production_daily를 다시 만든다")
    @PostMapping("/production-daily/aggregate")
    public List<OeeDaily> aggregate(@RequestParam LocalDate date) {
        aggregationService.aggregate(date);
        return oeeMapper.selectDaily(date);
    }
}
