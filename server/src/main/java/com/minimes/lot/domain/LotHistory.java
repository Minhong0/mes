package com.minimes.lot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** LOT에 일어난 일 한 건 (입고, 생산, 투입, 취소 등). 테이블: lot_history. 고치거나 지우지 않고 쌓기만 한다. */
@Entity
@Table(name = "lot_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    private String lotNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LotEventType eventType;

    private String processCode;

    @Column(precision = 12, scale = 3)
    private BigDecimal qty;

    private String equipCode;

    private String workerId;

    private String note;

    private LocalDateTime eventAt;

    private Long resultId;                  // 이 이력을 만든 실적 (입고는 null)

    public static LotHistory of(String lotNo, LotEventType eventType, BigDecimal qty, String processCode,
                                String equipCode, String workerId, Long resultId, String note, LocalDateTime now) {
        LotHistory h = new LotHistory();
        h.lotNo = lotNo;
        h.eventType = eventType;
        h.qty = qty;
        h.processCode = processCode;
        h.equipCode = equipCode;
        h.workerId = workerId;
        h.resultId = resultId;
        h.note = note;
        h.eventAt = now;
        return h;
    }
}
