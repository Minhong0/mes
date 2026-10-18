package com.minimes.lot.domain;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LOT: 같은 입고, 같은 실적으로 들어오거나 만들어진 물건의 묶음. 테이블: lot
 *
 * 원자재 LOT은 입고할 때, 반제품·완제품 LOT은 실적을 등록할 때 양품 수량으로 만든다.
 * 다음 공정에 투입하면 남은 수량(qty)이 줄고, 0이 되면 CONSUMED가 된다.
 */
@Entity
@Table(name = "lot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lot {

    @Id
    private String lotNo;

    private String itemCode;

    @Column(precision = 12, scale = 3)
    private BigDecimal qty;                 // 남은 수량

    @Column(precision = 12, scale = 3)
    private BigDecimal initialQty;          // 처음 수량

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LotStatus status;

    private String woNo;                    // 만든 작업지시. 원자재 LOT은 null

    private String lastProcess;             // 만든 공정. 품번이 같은 연삭·세척·검사 LOT은 이것으로 구분한다

    private String supplierLotNo;           // 원자재: 공급사 LOT 번호

    private LocalDateTime createdAt;

    private String createdBy;

    /** 원자재 입고 */
    public static Lot receive(String lotNo, String itemCode, BigDecimal qty, String supplierLotNo, String workerId,
                              LocalDateTime now) {
        Lot lot = create(lotNo, itemCode, qty, workerId, now);
        lot.supplierLotNo = supplierLotNo;
        return lot;
    }

    /** 실적의 양품으로 만든 LOT */
    public static Lot produce(String lotNo, String itemCode, BigDecimal qty, String woNo, String processCode,
                              String workerId, LocalDateTime now) {
        Lot lot = create(lotNo, itemCode, qty, workerId, now);
        lot.woNo = woNo;
        lot.lastProcess = processCode;
        return lot;
    }

    private static Lot create(String lotNo, String itemCode, BigDecimal qty, String workerId, LocalDateTime now) {
        if (qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "LOT 수량은 0보다 커야 합니다.");
        }
        Lot lot = new Lot();
        lot.lotNo = lotNo;
        lot.itemCode = itemCode;
        lot.qty = qty;
        lot.initialQty = qty;
        lot.status = LotStatus.ACTIVE;
        lot.createdAt = now;
        lot.createdBy = workerId;
        return lot;
    }

    /** 다음 공정에 투입. 남은 수량보다 많이 쓸 수 없다. */
    public void consume(BigDecimal used) {
        if (status != LotStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s 상태의 LOT은 투입할 수 없습니다: %s".formatted(status, lotNo));
        }
        if (used.compareTo(qty) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "LOT %s의 남은 수량(%s)보다 많이 투입할 수 없습니다.".formatted(lotNo, qty.stripTrailingZeros().toPlainString()));
        }
        qty = qty.subtract(used);
        if (qty.signum() == 0) {
            status = LotStatus.CONSUMED;
        }
    }

    /** 실적 취소: 투입했던 수량을 돌려받는다. */
    public void restore(BigDecimal used) {
        if (status != LotStatus.ACTIVE && status != LotStatus.CONSUMED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s 상태의 LOT에는 수량을 되돌릴 수 없습니다: %s".formatted(status, lotNo));
        }
        qty = qty.add(used);
        status = LotStatus.ACTIVE;
    }

    /** 실적 취소: 그 실적으로 만든 LOT을 없앤다. 이미 다음 공정에 쓰기 시작했으면 먼저 그 실적을 취소해야 한다. */
    public void cancelProduced() {
        if (status != LotStatus.ACTIVE || qty.compareTo(initialQty) != 0) {
            throw new BusinessException(ErrorCode.LOT_IN_USE,
                    "LOT %s가 이미 다음 공정에 투입되었습니다. 다음 공정의 실적을 먼저 취소하세요.".formatted(lotNo));
        }
        qty = BigDecimal.ZERO;
        status = LotStatus.SCRAPPED;
    }
}
