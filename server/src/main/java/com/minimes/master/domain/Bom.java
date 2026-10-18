package com.minimes.master.domain;

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

/**
 * BOM 한 줄: 상위 품목 1개를 만드는 데 하위 품목이 얼마나, 어느 공정에서 들어가는가. 테이블: bom
 * BOM 전개(재귀)는 BomMapper가 하고, 이 엔티티는 공정별 투입 자재를 찾을 때 쓴다.
 */
@Entity
@Table(name = "bom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer bomId;

    private String parentItemCode;

    private String childItemCode;

    @Column(precision = 12, scale = 4)
    private BigDecimal qty;          // 상위 품목 1개당 소요량

    private String unit;

    private String processCode;      // 하위 품목이 투입되는 공정
}
