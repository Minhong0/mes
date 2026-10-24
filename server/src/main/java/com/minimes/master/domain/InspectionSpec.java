package com.minimes.master.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 검사 기준: 공정에서 재는 항목과 허용 범위. 테이블: inspection_spec
 * 하한(lsl)이나 상한(usl) 중 하나는 없을 수 있다. 예: 표면 거칠기는 상한만, 경도는 하한·상한 모두.
 */
@Entity
@Table(name = "inspection_spec")
@IdClass(InspectionSpecId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionSpec {

    @Id
    private String processCode;

    @Id
    private String inspectCode;

    private String inspectName;

    private String equipCode;       // 재는 측정기 (예: CMM-01). 없으면 null

    private String unit;

    @Column(precision = 12, scale = 4)
    private BigDecimal nominal;     // 기준값 (표시용)

    @Column(precision = 12, scale = 4)
    private BigDecimal lsl;         // 하한. null이면 하한 없음

    @Column(precision = 12, scale = 4)
    private BigDecimal usl;         // 상한. null이면 상한 없음

    /** 하한·상한 안이면(경계 포함) 합격 */
    public boolean accepts(BigDecimal value) {
        return (lsl == null || value.compareTo(lsl) >= 0) && (usl == null || value.compareTo(usl) <= 0);
    }
}
