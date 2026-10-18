package com.minimes.equipment.web;

import com.minimes.equipment.domain.Downtime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** POP 정지 사유 입력 API의 요청·응답 형식. */
public final class DowntimeDtos {

    private DowntimeDtos() {
    }

    /** @param dtSub 소분류 (비워도 된다) */
    public record ReasonRequest(
            @NotBlank(message = "작업자는 필수입니다.") String workerId,
            @NotBlank(message = "정지 대분류는 필수입니다.") String dtCat,
            String dtSub,
            @Size(max = 200) String note
    ) {
    }

    /**
     * @param endedAt     null이면 아직 정지 중
     * @param durationMin 끝난 정지만 있다
     * @param reasonAt    작업자가 사유를 확정한 시각. null이면 설비가 보낸 사유 그대로
     */
    public record DowntimeResponse(long downtimeId, String equipCode, LocalDateTime startedAt, LocalDateTime endedAt,
                                   Integer durationMin, String dtCat, String dtSub, String note, String workerId,
                                   LocalDateTime reasonAt) {
        public static DowntimeResponse from(Downtime d) {
            return new DowntimeResponse(d.getDowntimeId(), d.getEquipCode(), d.getStartedAt(), d.getEndedAt(),
                    d.getDurationMin(), d.getDtCat(), d.getDtSub(), d.getNote(), d.getWorkerId(), d.getReasonAt());
        }
    }
}
