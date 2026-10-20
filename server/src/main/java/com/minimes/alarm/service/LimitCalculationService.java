package com.minimes.alarm.service;

import com.minimes.alarm.domain.EquipmentLimit;
import com.minimes.alarm.domain.EquipmentLimitId;
import com.minimes.alarm.domain.LimitMetric;
import com.minimes.alarm.mapper.SensorStats;
import com.minimes.alarm.mapper.SensorStatsMapper;
import com.minimes.alarm.repository.EquipmentLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기준 기간의 가동 데이터로 관리한계를 계산해 저장한다.
 *
 * 자동으로 다시 계산하지 않는다. 정상이었던 기간을 사람이 골라 정하고, 필요할 때 다시 정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LimitCalculationService {

    /** 표본이 이보다 적으면 평균·표준편차를 믿기 어려워 한계를 정하지 않는다 */
    public static final int MIN_SAMPLES = 30;

    private final SensorStatsMapper sensorStatsMapper;
    private final EquipmentLimitRepository limitRepository;
    private final Clock clock;

    /** [from, to] 날짜(측정 시각 기준)의 가동 데이터로 설비마다 진동·온도 한계를 정한다. 저장한 한계를 돌려준다. */
    public List<EquipmentLimit> calculate(LocalDate from, LocalDate to) {
        LocalDateTime baseFrom = from.atStartOfDay();
        LocalDateTime baseTo = to.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock);

        List<EquipmentLimit> saved = new ArrayList<>();
        for (SensorStats stats : sensorStatsMapper.selectRunStats(baseFrom, baseTo)) {
            if (stats.sampleCount() < MIN_SAMPLES) {
                log.info("[관리한계] {} 표본 {}개 < {}개, 건너뜀", stats.equipCode(), stats.sampleCount(), MIN_SAMPLES);
                continue;
            }
            saved.add(save(stats, LimitMetric.VIBRATION, stats.vibrationMean(), stats.vibrationSigma(), baseFrom, baseTo, now));
            saved.add(save(stats, LimitMetric.TEMP, stats.tempMean(), stats.tempSigma(), baseFrom, baseTo, now));
        }
        return saved;
    }

    private EquipmentLimit save(SensorStats stats, LimitMetric metric, BigDecimal mean, BigDecimal sigma,
                                LocalDateTime baseFrom, LocalDateTime baseTo, LocalDateTime now) {
        EquipmentLimit limit = limitRepository.findById(new EquipmentLimitId(stats.equipCode(), metric))
                .orElseGet(() -> new EquipmentLimit(stats.equipCode(), metric));
        limit.recalculate(mean, sigma, stats.sampleCount(), baseFrom, baseTo, now);
        return limitRepository.save(limit);
    }

    @Transactional(readOnly = true)
    public List<EquipmentLimit> findAll() {
        return limitRepository.findAllByOrderByEquipCodeAscMetricAsc();
    }
}
