package com.minimes.workorder.web;

import com.minimes.workorder.domain.Defect;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.domain.WorkResult.DefectLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** POP API(공정 시작·실적·완료·취소)의 요청·응답 형식. */
public final class OperationDtos {

    private OperationDtos() {
    }

    public record WorkerRequest(@NotBlank(message = "작업자는 필수입니다.") String workerId) {
    }

    /**
     * @param requestId POP가 만든 요청 ID(GUID). 응답을 못 받아 다시 보낼 때도 같은 값을 보낸다
     * @param equipCode 작업한 설비 (비워도 된다)
     */
    public record ReportRequest(
            @NotBlank(message = "요청 ID는 필수입니다.") @Size(max = 50) String requestId,
            @NotBlank(message = "작업자는 필수입니다.") String workerId,
            String equipCode,
            @PositiveOrZero(message = "양품 수량은 0 이상이어야 합니다.") int goodQty,
            @NotNull List<@Valid DefectRequest> defects
    ) {
        public List<DefectLine> defectLines() {
            return defects.stream().map(d -> new DefectLine(d.defectType(), d.qty())).toList();
        }
    }

    public record DefectRequest(
            @NotBlank(message = "불량 유형은 필수입니다.") String defectType,
            @Positive(message = "불량 수량은 1 이상이어야 합니다.") int qty
    ) {
    }

    public record CancelRequest(
            @NotBlank(message = "작업자는 필수입니다.") String workerId,
            @NotBlank(message = "취소 사유는 필수입니다.") @Size(max = 200) String reason
    ) {
    }

    public record ResultResponse(long resultId, long woOpId, String equipCode, String workerId,
                                 LocalDateTime startedAt, LocalDateTime endedAt, int goodQty, int defectQty,
                                 boolean cancelled, String cancelReason, List<DefectResponse> defects) {
        public static ResultResponse from(WorkResult r) {
            return new ResultResponse(r.getResultId(), r.getWoOpId(), r.getEquipCode(), r.getWorkerId(),
                    r.getStartedAt(), r.getEndedAt(), r.getGoodQty(), r.getDefectQty(), r.isCancelled(),
                    r.getCancelReason(), r.getDefects().stream().map(DefectResponse::from).toList());
        }
    }

    public record DefectResponse(String defectType, int qty) {
        public static DefectResponse from(Defect d) {
            return new DefectResponse(d.getDefectType(), d.getQty());
        }
    }
}
