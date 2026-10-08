package com.minimes.workorder.web;

import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 작업지시 API의 요청·응답 형식. */
public final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    /** 요청 값 검증은 Bean Validation 어노테이션으로 한다. 실패하면 GlobalExceptionHandler가 400으로 응답한다. */
    public record CreateRequest(
            @NotBlank(message = "품목코드는 필수입니다.") String itemCode,
            @Positive(message = "계획수량은 1 이상이어야 합니다.") int planQty,
            LocalDate dueDate,
            @Size(max = 200, message = "비고는 200자 이하입니다.") String note
    ) {
    }

    public record WorkOrderResponse(String woNo, String itemCode, int planQty, LocalDate dueDate, String status,
                                    LocalDateTime actualStart, LocalDateTime actualEnd, String note,
                                    LocalDateTime createdAt, List<OperationResponse> operations) {
        public static WorkOrderResponse from(WorkOrder wo) {
            return new WorkOrderResponse(wo.getWoNo(), wo.getItemCode(), wo.getPlanQty(), wo.getDueDate(),
                    wo.getStatus().name(), wo.getActualStart(), wo.getActualEnd(), wo.getNote(), wo.getCreatedAt(),
                    wo.getOperations().stream().map(OperationResponse::from).toList());
        }
    }

    public record OperationResponse(Long woOpId, int seq, String processCode, String wcCode, String status,
                                    int goodQty, int defectQty) {
        public static OperationResponse from(WorkOrderOperation op) {
            return new OperationResponse(op.getWoOpId(), op.getSeq(), op.getProcessCode(), op.getWcCode(),
                    op.getStatus().name(), op.getGoodQty(), op.getDefectQty());
        }
    }
}
