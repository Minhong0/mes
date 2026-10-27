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

import java.time.LocalDateTime;

/** 작업자. 테이블: worker. 사번과 비밀번호로 로그인하고(POP, 관리자 웹), 실적에 누가 했는지 남긴다. */
@Entity
@Table(name = "worker")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worker {

    @Id
    private String workerId;

    private String workerName;

    private String wcCode;          // 소속 작업장. 관리자는 null

    private String role;            // ADMIN, OPERATOR, QC

    /** 사용 여부 (Y/N). DB 타입이 CHAR(1)이라 타입을 명시한다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 1)
    private String useYn;

    private LocalDateTime createdAt;

    /** 비밀번호 BCrypt 해시. null이면 로그인할 수 없다 (시연용 비밀번호는 db/seed/03_demo_password.sql) */
    private String passwordHash;

    public boolean isActive() {
        return "Y".equals(useYn);
    }
}
