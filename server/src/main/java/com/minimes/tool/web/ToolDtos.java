package com.minimes.tool.web;

import com.minimes.tool.domain.Tool;
import com.minimes.tool.domain.ToolChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 공구 API의 요청·응답 형식. */
public final class ToolDtos {

    private ToolDtos() {
    }

    public record ChangeRequest(
            @Size(max = 100, message = "사유는 100자까지입니다.") String reason
    ) {
    }

    /**
     * state: OK(정상), WARN(교체 임박, 수명의 90% 이상), REPLACE(교체 필요, 수명 도달).
     * installedAt: 설치한 때 (공장 시계). null이면 설비 데이터가 아직 없어 세기 시작 전
     */
    public record ToolResponse(String toolCode, String toolName, String toolType, String equipCode, String equipName,
                               int lifeCount, int usedCount, int remaining, String state, LocalDateTime installedAt) {
        public static ToolResponse from(Tool t, String equipName) {
            return new ToolResponse(t.getToolCode(), t.getToolName(), t.getToolType().name(), t.getEquipCode(), equipName,
                    t.getLifeCount(), t.getUsedCount(), Math.max(0, t.getLifeCount() - t.getUsedCount()), t.state().name(),
                    t.getInstalledAt());
        }
    }

    /** 교체 결과: 새 공구와 남긴 이력. 화면은 이력의 usedCount(서버가 바꾸기 직전에 다시 센 수량)를 보여 준다 */
    public record ChangeResultResponse(ToolResponse tool, ChangeResponse change) {
    }

    /** usedCount: 바꾼 공구로 가공한 수량 */
    public record ChangeResponse(long changeId, String toolCode, LocalDateTime changedAt, int usedCount, String reason,
                                 String workerId) {
        public static ChangeResponse from(ToolChange c) {
            return new ChangeResponse(c.getChangeId(), c.getToolCode(), c.getChangedAt(), c.getUsedCount(), c.getReason(),
                    c.getWorkerId());
        }
    }
}
