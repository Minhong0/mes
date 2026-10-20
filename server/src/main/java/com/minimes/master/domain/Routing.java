package com.minimes.master.domain;

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

/**
 * 라우팅: 품목을 만드는 공정 순서. 테이블: routing
 * 기본키가 (item_code, seq) 두 컬럼이라 @IdClass로 복합키를 표현한다.
 */
@Entity
@Table(name = "routing")
@IdClass(RoutingId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routing {

    @Id
    private String itemCode;

    @Id
    private Integer seq;

    private String processCode;

    private String wcCode;

    private String outputItemCode;

    @Column(precision = 10, scale = 2)
    private BigDecimal stdTimeMin;

    /** EA면 개당 표준시간, BATCH면 Batch당 표준시간 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TimeBasis timeBasis;

    private Integer batchMaxQty;

    @Column(precision = 10, scale = 2)
    private BigDecimal setupTimeMin;

    /** 이 공정에서 만드는 LOT 하나의 최대 수량. 양품이 더 많으면 나눈다 (포장 트레이). null이면 나누지 않는다 */
    private Integer lotSize;

    public enum TimeBasis { EA, BATCH }

    public static Routing of(String itemCode, int seq, String processCode, String wcCode, String outputItemCode,
                             BigDecimal stdTimeMin, TimeBasis timeBasis, Integer batchMaxQty, BigDecimal setupTimeMin) {
        Routing r = new Routing();
        r.itemCode = itemCode;
        r.seq = seq;
        r.processCode = processCode;
        r.wcCode = wcCode;
        r.outputItemCode = outputItemCode;
        r.stdTimeMin = stdTimeMin;
        r.timeBasis = timeBasis;
        r.batchMaxQty = batchMaxQty;
        r.setupTimeMin = setupTimeMin;
        return r;
    }
}
