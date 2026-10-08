package com.minimes.master.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공통코드 (비가동 사유, 불량 유형, 상태값 등). 테이블: common_code */
@Entity
@Table(name = "common_code")
@IdClass(CommonCodeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCode {

    @Id
    private String groupCode;

    @Id
    private String code;

    private String codeName;

    /** 상위 코드. 예: 비가동 소분류 BRK_SPINDLE의 상위는 대분류 BREAKDOWN */
    private String parentCode;

    private Integer sortOrder;
}
