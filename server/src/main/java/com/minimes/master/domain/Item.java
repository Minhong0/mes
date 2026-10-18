package com.minimes.master.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 품목 (완제품, 반제품, 원자재). 테이블: item */
@Entity
@Table(name = "item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA용 기본 생성자. 외부에서 빈 객체를 만들지 못하게 막는다.
public class Item {

    @Id
    private String itemCode;

    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ItemType itemType;

    private String spec;

    private String unit;

    private String note;

    /** LOT 관리 여부 (Y/N). Y인 품목은 실적을 등록할 때 투입 LOT을 골라야 한다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 1)
    private String lotYn;

    public boolean isLotTracked() {
        return "Y".equals(lotYn);
    }
}
