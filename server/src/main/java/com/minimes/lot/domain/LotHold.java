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

/**
 * 보류 건 하나: 어느 LOT 때문에(원인 LOT), 왜, 누가 보류했나. 테이블: lot_hold.
 * 이 보류가 막은 LOT은 LotHoldTarget에 따로 남는다. LOT은 열린 보류 건이 하나라도 있으면 보류 상태다.
 */
@Entity
@Table(name = "lot_hold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holdId;

    private String originLotNo;

    private String reason;

    private boolean byInspection;        // 검사 불합격 자동 보류

    private String heldBy;

    private LocalDateTime heldAt;

    public static LotHold of(String originLotNo, String reason, boolean byInspection, String heldBy, LocalDateTime now) {
        LotHold h = new LotHold();
        h.originLotNo = originLotNo;
        h.reason = reason;
        h.byInspection = byInspection;
        h.heldBy = heldBy;
        h.heldAt = now;
        return h;
    }
}
