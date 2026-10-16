package com.minimes.production.domain;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 설비·일자별 생산 집계. 테이블: production_daily
 * OEE 뷰(v_oee_daily, v_oee_monthly)가 이 테이블만 보고 계산하므로, 여기를 채우면 OEE가 나온다.
 *
 * run_min(가동 시간 = 계획 − 비가동)은 DB의 계산 컬럼이라 엔티티에 두지 않는다. 넣으려 하면 DB가 거부한다.
 */
@Entity
@Table(name = "production_daily")
@IdClass(ProductionDailyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionDaily {

    @Id
    private LocalDate prodDate;

    @Id
    private String equipCode;

    private Integer plannedMin;     // 계획 시간 (분)

    private Integer downtimeMin;    // 비가동 시간 (분)

    private Integer prodQty;        // 생산 수량

    private Integer goodQty;        // 양품

    private Integer defectQty;      // 불량

    @Column(precision = 10, scale = 2)
    private BigDecimal idealCtMin;  // 집계 당시의 이론 사이클타임 (기준정보가 나중에 바뀌어도 그날 OEE는 그대로)

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ProductionSource source;

    private LocalDateTime createdAt;

    public static ProductionDaily ofMes(LocalDate prodDate, String equipCode, int plannedMin, int downtimeMin,
                                        int prodQty, BigDecimal idealCtMin, LocalDateTime now) {
        ProductionDaily daily = new ProductionDaily();
        daily.prodDate = prodDate;
        daily.equipCode = equipCode;
        daily.source = ProductionSource.MES;
        daily.createdAt = now;
        daily.updateFromMes(plannedMin, downtimeMin, prodQty, idealCtMin);
        return daily;
    }

    /**
     * 다시 집계한 값으로 바꾼다. 같은 날을 여러 번 집계해도 마지막 값만 남는다.
     * 불량 수량은 아직 입력하는 곳이 없어(POP 검사 입력 예정) 모두 양품으로 둔다.
     */
    public void updateFromMes(int plannedMin, int downtimeMin, int prodQty, BigDecimal idealCtMin) {
        if (source != ProductionSource.MES) {
            throw new IllegalStateException("부트캠프 실적은 덮어쓸 수 없습니다: " + prodDate + " " + equipCode);
        }
        this.plannedMin = plannedMin;
        this.downtimeMin = downtimeMin;
        this.prodQty = prodQty;
        this.goodQty = prodQty;
        this.defectQty = 0;
        this.idealCtMin = idealCtMin;
    }
}
