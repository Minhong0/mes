package com.minimes.workorder.domain;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.master.domain.Routing;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 작업지시. 테이블: work_order
 *
 * 상태를 바꾸는 규칙(start, complete, close)을 서비스가 아니라 엔티티 안에 둔다.
 * 그러면 "대기 상태에서만 시작할 수 있다" 같은 규칙이 한곳에만 있고, DB 없이 단위 테스트로 검증할 수 있다.
 */
@Entity
@Table(name = "work_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 생성자 접근 x, 설계: create로만 작동하게
public class WorkOrder {

    @Id
    private String woNo;

    private String itemCode;

    private Integer planQty;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private WorkOrderStatus status;

    private LocalDateTime actualStart;

    private LocalDateTime actualEnd;

    private String note;

    private LocalDateTime createdAt;

    // 작업지시를 저장하면 공정별 작업도 함께 저장된다 (cascade). 작업지시에서 빠진 공정은 삭제된다 (orphanRemoval).
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true) // 1 : N , 자식을 workOrder로 설정

    @OrderBy("seq")
    private List<WorkOrderOperation> operations = new ArrayList<>();


    // 라우팅을 따라 공정별 작업을 만들어 작업지시를 생성한다.
    public static WorkOrder create(String woNo, String itemCode, int planQty, LocalDate dueDate, String note,
                                   List<Routing> routings, LocalDateTime now) {
        if (routings.isEmpty()) {
            throw new BusinessException(ErrorCode.ROUTING_NOT_FOUND, "라우팅이 없는 품목은 작업지시를 만들 수 없습니다: " + itemCode);
        }
        WorkOrder wo = new WorkOrder();
        wo.woNo = woNo;
        wo.itemCode = itemCode;
        wo.planQty = planQty;
        wo.dueDate = dueDate;
        wo.note = note;
        wo.status = WorkOrderStatus.WAIT;
        wo.createdAt = now;
        routings.forEach(r -> wo.operations.add(WorkOrderOperation.fromRouting(wo, r)));
        return wo;
    }

    /** 대기 → 진행 */
    public void start(LocalDateTime now) {
        requireStatus(WorkOrderStatus.WAIT, "시작");
        status = WorkOrderStatus.RUN;
        actualStart = now;
    }

    /** 진행 → 완료 */
    public void complete(LocalDateTime now) {
        requireStatus(WorkOrderStatus.RUN, "완료");
        status = WorkOrderStatus.DONE;
        actualEnd = now;
    }

    /** 완료 → 마감. 마감 후에는 실적을 더 받지 않는다. */
    public void close() {
        requireStatus(WorkOrderStatus.DONE, "마감");
        status = WorkOrderStatus.CLOSED;
    }

    /** 모든 공정이 끝났으면 진행 → 완료 */
    void completeIfAllOperationsDone(LocalDateTime now) {
        boolean allDone = operations.stream().allMatch(op -> op.getStatus() == WorkOrderOperation.Status.DONE);
        if (allDone && status == WorkOrderStatus.RUN) {
            complete(now);
        }
    }

    /** 공정을 시작하거나 실적을 받으려면 작업지시가 진행 중이어야 한다. */
    void requireAcceptingResults() {
        if (status != WorkOrderStatus.RUN) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s 상태의 작업지시에는 작업할 수 없습니다. (RUN 상태에서만 가능)".formatted(status));
        }
    }

    /** 마감한 작업지시는 실적을 바꿀 수 없다 (취소 포함). */
    void requireNotClosed() {
        if (status == WorkOrderStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "마감한 작업지시의 실적은 바꿀 수 없습니다.");
        }
    }

    public List<WorkOrderOperation> getOperations() {
        return Collections.unmodifiableList(operations);   // 밖에서 목록을 직접 고치지 못하게 막는다
    }

    private void requireStatus(WorkOrderStatus expected, String action) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s 상태의 작업지시는 %s할 수 없습니다. (%s 상태에서만 가능)".formatted(status, action, expected));
        }
    }
}
