package com.minimes.master.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** 설비. 테이블: equipment */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    private String equipCode;

    private String equipName;

    private String wcCode;

    private String equipType;

    private String maker;

    private String model;

    private Short installYear;            // DB 타입이 SMALLINT

    /** 이론 사이클타임(분/개). OEE 성능효율 계산에 쓴다. */
    @Column(precision = 10, scale = 2)
    private BigDecimal idealCtMin;

    private Integer plannedMinPerDay;

    /** 설비 데이터 자동 수집 여부 (Y/N). DB 타입이 CHAR(1)이라 타입을 명시한다. */
    @JdbcTypeCode(SqlTypes.CHAR) // Hibernate는 String은 기본으로 VARCHAR라고 인식 하므로 CHAR라고 따로 지정이 필요
    @Column(length = 1)
    private String autoCollect;

    private String status;

    private String note;

    public boolean isAutoCollect() {
        return "Y".equals(autoCollect);
    }

    /** 현재 상태를 바꾼다. 설비 데이터 수집기가 상태가 바뀔 때 부른다 (공통코드 EQUIP_STATUS 값). */
    public void changeStatus(String status) {
        this.status = status;
    }
}
