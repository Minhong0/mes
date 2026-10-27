package com.minimes.lot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 보류 건이 막은 LOT 하나. 테이블: lot_hold_target. 해제는 LOT마다 따로 한다 (releasedAt이 null이면 아직 묶여 있다). */
@Entity
@Table(name = "lot_hold_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotHoldTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long targetId;

    private Long holdId;

    private String lotNo;

    private String releasedBy;

    private LocalDateTime releasedAt;

    private String releaseReason;

    public static LotHoldTarget of(Long holdId, String lotNo) {
        LotHoldTarget t = new LotHoldTarget();
        t.holdId = holdId;
        t.lotNo = lotNo;
        return t;
    }

    public void release(String by, String reason, LocalDateTime now) {
        releasedBy = by;
        releaseReason = reason;
        releasedAt = now;
    }
}
