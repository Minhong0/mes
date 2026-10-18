package com.minimes.lot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LOT 계보 한 줄: 투입 LOT이 산출 LOT에 얼마나 들어갔는가. 테이블: lot_genealogy
 * 투입 LOT 여러 개로 산출 LOT 하나를 만들면(열처리 Batch) 줄이 여러 개가 된다.
 * 실적을 취소해도 지우지 않는다. 추적 쿼리가 취소한 실적의 줄을 빼고 따라간다.
 */
@Entity
@Table(name = "lot_genealogy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotGenealogy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long genealogyId;

    private String inputLotNo;

    private String outputLotNo;

    @Column(precision = 12, scale = 3)
    private BigDecimal qty;                 // 투입한 수량 (불량이 난 것 포함)

    private String processCode;

    private Long resultId;

    private LocalDateTime linkedAt;

    public static LotGenealogy link(String inputLotNo, String outputLotNo, BigDecimal qty, String processCode,
                                    Long resultId, LocalDateTime now) {
        LotGenealogy g = new LotGenealogy();
        g.inputLotNo = inputLotNo;
        g.outputLotNo = outputLotNo;
        g.qty = qty;
        g.processCode = processCode;
        g.resultId = resultId;
        g.linkedAt = now;
        return g;
    }
}
