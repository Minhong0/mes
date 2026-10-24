package com.minimes.quality.domain;

import com.minimes.master.domain.InspectionSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 측정값 한 개: LOT의 한 검사 항목, 한 시료. 판정은 서버가 검사 기준의 하한·상한으로 정한다. 테이블: inspection_result */
@Entity
@Table(name = "inspection_result", uniqueConstraints = @UniqueConstraint(
        name = "ux_inspection_result_sample", columnNames = {"lot_no", "process_code", "inspect_code", "sample_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inspId;

    private String lotNo;

    private String processCode;

    private String inspectCode;

    private Integer sampleNo;

    @Column(precision = 12, scale = 4)
    private BigDecimal measuredValue;

    /** DB 타입이 CHAR(2)라 타입을 명시한다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private Judgement judgement;

    private String equipCode;

    private String workerId;

    private LocalDateTime measuredAt;

    public static InspectionResult measure(String lotNo, InspectionSpec spec, int sampleNo, BigDecimal value,
                                           String workerId, LocalDateTime now) {
        InspectionResult r = new InspectionResult();
        r.lotNo = lotNo;
        r.processCode = spec.getProcessCode();
        r.inspectCode = spec.getInspectCode();
        r.sampleNo = sampleNo;
        r.measuredValue = value;
        r.judgement = spec.accepts(value) ? Judgement.OK : Judgement.NG;
        r.equipCode = spec.getEquipCode();
        r.workerId = workerId;
        r.measuredAt = now;
        return r;
    }
}
