package com.minimes.tool.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 설비에 달린 공구 하나와 지금까지의 사용 수량. 테이블: tool
 *
 * 사용 수량(usedCount)은 설치한 뒤 그 설비의 누적 카운트가 늘어난 양이다 (ToolLifeService가 주기적으로 다시 센다).
 * 수명(lifeCount)에 이르면 바꿀 때가 된 것이다. 바꾸면 설치 시각을 새로 잡고 0부터 다시 센다.
 * 설치 시각은 설비의 시계(공장 시계)로 적는다. 카운트가 같은 시계로 오기 때문이다.
 */
@Entity
@Table(name = "tool")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tool {

    /** 이 비율을 넘으면 "교체 임박"으로 보여 준다 */
    public static final double WARN_RATIO = 0.9;

    @Id
    private String toolCode;

    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ToolType toolType;

    private String equipCode;

    private Integer lifeCount;              // 교체 기준 가공 수량

    private Integer usedCount;              // 지금 공구로 가공한 수량

    private LocalDateTime installedAt;      // 설치한 때 (공장 시계). null이면 아직 세기 시작 전

    public static Tool of(String toolCode, String toolName, ToolType toolType, String equipCode, int lifeCount) {
        Tool tool = new Tool();
        tool.toolCode = toolCode;
        tool.toolName = toolName;
        tool.toolType = toolType;
        tool.equipCode = equipCode;
        tool.lifeCount = lifeCount;
        tool.usedCount = 0;
        return tool;
    }

    public boolean isCounting() {
        return installedAt != null;
    }

    /** 새 공구를 달았다 (또는 처음으로 세기 시작한다): 이 시각부터 0개 */
    public void install(LocalDateTime machineTime) {
        installedAt = machineTime;
        usedCount = 0;
    }

    public void updateUsed(int used) {
        usedCount = used;
    }

    public boolean isLifeReached() {
        return usedCount >= lifeCount;
    }

    public ToolState state() {
        if (isLifeReached()) {
            return ToolState.REPLACE;
        }
        return usedCount >= lifeCount * WARN_RATIO ? ToolState.WARN : ToolState.OK;
    }

    public enum ToolState {
        OK,        // 정상
        WARN,      // 교체 임박 (수명의 90% 이상)
        REPLACE    // 교체 필요 (수명 도달)
    }
}
