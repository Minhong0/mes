package com.minimes.tool.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 공구 교체 한 건. 바꾸기 전까지 몇 개를 가공했는지 남긴다. 테이블: tool_change_history */
@Entity
@Table(name = "tool_change_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ToolChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long changeId;

    private String toolCode;

    private LocalDateTime changedAt;    // 공장 시계

    private Integer usedCount;          // 바꾼 공구로 가공한 수량

    private String reason;

    private String workerId;

    public static ToolChange of(Tool replaced, LocalDateTime changedAt, String reason, String workerId) {
        ToolChange change = new ToolChange();
        change.toolCode = replaced.getToolCode();
        change.changedAt = changedAt;
        change.usedCount = replaced.getUsedCount();
        change.reason = reason;
        change.workerId = workerId;
        return change;
    }
}
