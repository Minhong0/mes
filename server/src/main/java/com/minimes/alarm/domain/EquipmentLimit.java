package com.minimes.alarm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 설비·센서 항목별 관리한계. 테이블: equipment_limit
 * 기준 기간의 가동 데이터로 계산한다: UCL = 평균 + 3σ.
 */
@Entity
@Table(name = "equipment_limit")
@IdClass(EquipmentLimitId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentLimit {

    static final BigDecimal SIGMA_MULTIPLIER = BigDecimal.valueOf(3);

    @Id
    private String equipCode;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LimitMetric metric;

    private BigDecimal centerValue;

    private BigDecimal sigma;

    private BigDecimal ucl;

    private int sampleCount;

    private LocalDateTime baseFrom;

    private LocalDateTime baseTo;

    private LocalDateTime calculatedAt;

    public EquipmentLimit(String equipCode, LimitMetric metric) {
        this.equipCode = equipCode;
        this.metric = metric;
    }

    /** 기준 기간의 평균·표준편차로 한계를 (다시) 정한다. */
    public void recalculate(BigDecimal mean, BigDecimal sigma, int sampleCount,
                            LocalDateTime baseFrom, LocalDateTime baseTo, LocalDateTime now) {
        this.centerValue = mean.setScale(3, RoundingMode.HALF_UP);
        this.sigma = sigma.setScale(4, RoundingMode.HALF_UP);
        this.ucl = mean.add(sigma.multiply(SIGMA_MULTIPLIER)).setScale(3, RoundingMode.HALF_UP);
        this.sampleCount = sampleCount;
        this.baseFrom = baseFrom;
        this.baseTo = baseTo;
        this.calculatedAt = now;
    }

    public boolean isExceededBy(BigDecimal value) {
        return value.compareTo(ucl) > 0;
    }
}
