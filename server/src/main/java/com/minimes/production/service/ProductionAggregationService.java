package com.minimes.production.service;

import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.domain.EquipmentStatusLog;
import com.minimes.equipment.repository.CollectedRange;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.equipment.repository.EquipmentStatusLogRepository;
import com.minimes.equipment.service.ProducedCounter;
import com.minimes.master.domain.Equipment;
import com.minimes.master.repository.EquipmentRepository;
import com.minimes.production.domain.ProductionDaily;
import com.minimes.production.domain.ProductionDailyId;
import com.minimes.production.domain.ProductionSource;
import com.minimes.production.repository.ProductionDailyRepository;
import com.minimes.workorder.repository.WorkResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 설비 수집 데이터로 일별 생산 실적(production_daily, source = MES)을 만든다.
 *
 *   생산 수량   = 그날 메시지의 누적 카운트(cycleCount)가 늘어난 양의 합 (ProducedCounter)
 *   계획 시간   = 그날 상태 이력이 덮는 시간 (설비 데이터가 있었던 시간)
 *   비가동 시간 = 그중 DOWN, SETUP 상태였던 시간
 *   불량 수량   = 그날 POP에서 그 설비로 등록한 불량의 합 (취소한 실적 제외). 양품 = 생산 − 불량
 *
 * 같은 날을 몇 번 다시 집계해도 결과가 같다(멱등). 그래서 늦게 도착한 데이터가 있으면 그날을 다시 집계하면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductionAggregationService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentDataRepository equipmentDataRepository;
    private final EquipmentStatusLogRepository statusLogRepository;
    private final ProductionDailyRepository productionDailyRepository;
    private final WorkResultRepository workResultRepository;
    private final ProducedCounter producedCounter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** since 이후에 받은 데이터가 걸쳐 있는 날짜들 (측정 시각 기준). 받은 것이 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<LocalDate> datesReceivedSince(LocalDateTime since) {
        CollectedRange range = equipmentDataRepository.findCollectedRangeReceivedSince(since);
        if (range.isEmpty()) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = range.from().toLocalDate(); !d.isAfter(range.to().toLocalDate()); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    /** 하루치를 자동 수집 설비(CNC01~05)마다 집계해 저장한다. 저장한 행 수를 돌려준다. */
    public int aggregate(LocalDate date) {
        int saved = 0;
        for (Equipment equipment : equipmentRepository.findAllByOrderByEquipCode()) {
            if (equipment.isAutoCollect() && aggregate(date, equipment)) {
                saved++;
            }
        }
        if (saved > 0) {
            eventPublisher.publishEvent(new ProductionAggregatedEvent(date));
        }
        return saved;
    }

    private boolean aggregate(LocalDate date, Equipment equipment) {
        String equipCode = equipment.getEquipCode();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        Optional<ProductionDaily> existing = productionDailyRepository.findById(new ProductionDailyId(date, equipCode));
        if (existing.isPresent() && existing.get().getSource() != ProductionSource.MES) {
            return false;   // 부트캠프 과거 실적이 있는 날은 건드리지 않는다
        }

        TimeTotals time = sumTime(equipCode, from, to);
        if (time.plannedMin() == 0) {
            return false;   // 그날 데이터가 없다 (production_daily는 계획 시간이 0보다 커야 한다)
        }
        int prodQty = producedCounter.count(equipCode, from, to);
        int defectQty = countDefects(equipCode, from, to, prodQty);

        if (existing.isPresent()) {
            existing.get().updateFromMes(time.plannedMin(), time.downtimeMin(), prodQty, defectQty, equipment.getIdealCtMin());
        } else {
            productionDailyRepository.save(ProductionDaily.ofMes(date, equipCode, time.plannedMin(), time.downtimeMin(),
                    prodQty, defectQty, equipment.getIdealCtMin(), LocalDateTime.now(clock)));
        }
        log.debug("[집계] {} {} 계획 {}분, 비가동 {}분, 생산 {}개, 불량 {}개", date, equipCode, time.plannedMin(), time.downtimeMin(),
                prodQty, defectQty);
        return true;
    }

    /**
     * 상태 이력을 하루 구간으로 잘라 시간을 더한다.
     *
     *   하루:        |00:00 ──────────────────────────────────────── 24:00|
     *   상태 이력:   RUN 어제 22:00 ~ 01:00 | DOWN 01:00 ~ 01:30 | RUN 01:30 ~ (진행 중)
     *   더하는 부분:     00:00 ~ 01:00        01:00 ~ 01:30        01:30 ~ 마지막 메시지 시각
     *
     * 진행 중인 상태는 "마지막 메시지 시각"까지만 센다. 공장 시계는 시뮬레이터가 정하므로 서버의 현재 시각을 쓰면 안 된다.
     * 초 단위로 더한 뒤 마지막에 분으로 반올림한다 (행마다 반올림하면 오차가 쌓인다).
     */
    private TimeTotals sumTime(String equipCode, LocalDateTime from, LocalDateTime to) {
        List<EquipmentStatusLog> logs = statusLogRepository.findOverlapping(equipCode, from, to);
        if (logs.isEmpty()) {
            return new TimeTotals(0, 0);
        }
        LocalDateTime lastSeen = equipmentDataRepository.findFirstByEquipCodeOrderByCollectedAtDesc(equipCode)
                .map(EquipmentData::getCollectedAt)
                .orElse(from);

        long plannedSeconds = 0;
        long stoppedSeconds = 0;
        for (EquipmentStatusLog statusLog : logs) {
            LocalDateTime start = max(statusLog.getStartedAt(), from);
            LocalDateTime end = min(statusLog.getEndedAt() != null ? statusLog.getEndedAt() : lastSeen, to);
            long seconds = Math.max(0, Duration.between(start, end).toSeconds());
            plannedSeconds += seconds;
            if (statusLog.getStatus().isStopped()) {
                stoppedSeconds += seconds;
            }
        }
        return new TimeTotals(toMinutes(plannedSeconds), toMinutes(stoppedSeconds));
    }

    /**
     * 그날 POP 불량 수량. 생산 수량(설비 카운트)이 기준이므로 그보다 많을 수는 없다.
     * 더 많다면 카운트가 빠졌거나 입력을 잘못한 것이다. 경고를 남기고 생산 수량까지만 반영한다 (OEE 품질 0%).
     */
    private int countDefects(String equipCode, LocalDateTime from, LocalDateTime to, int prodQty) {
        int defectQty = (int) workResultRepository.sumDefectQty(equipCode, from, to);
        if (defectQty > prodQty) {
            log.warn("[집계] {} {} 불량 {}개가 설비 생산 수량 {}개보다 많습니다. 생산 수량까지만 반영합니다.",
                    from.toLocalDate(), equipCode, defectQty, prodQty);
            return prodQty;
        }
        return defectQty;
    }

    private static int toMinutes(long seconds) {
        return (int) Math.round(seconds / 60.0);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private record TimeTotals(int plannedMin, int downtimeMin) {
    }
}
