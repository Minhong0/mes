package com.minimes.master.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공정 (OP-10 선삭 ~ OP-70 포장). 테이블: process
 * 클래스 이름을 Process로 하면 java.lang.Process와 겹쳐서 ManufacturingProcess로 지었다.
 */
@Entity
@Table(name = "process")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManufacturingProcess {

    @Id
    private String processCode;

    private String processName;

    private String processNameEn;

    private String department;
}
