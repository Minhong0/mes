package com.minimes.workorder.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 실적 한 건의 불량 내역 (유형별 수량). 테이블: defect */
@Entity
@Table(name = "defect")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Defect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long defectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private WorkResult workResult;

    private String defectType;      // 공통코드 DEFECT_TYPE

    private int qty;

    private String note;

    private LocalDateTime createdAt;

    static Defect of(WorkResult workResult, String defectType, int qty, LocalDateTime now) {
        Defect defect = new Defect();
        defect.workResult = workResult;
        defect.defectType = defectType;
        defect.qty = qty;
        defect.createdAt = now;
        return defect;
    }
}
