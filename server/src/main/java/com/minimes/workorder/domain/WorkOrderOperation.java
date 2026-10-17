package com.minimes.workorder.domain;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.master.domain.Routing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 작업지시의 공정별 작업. 테이블: work_order_operation
 * 작업지시를 만들 때 라우팅을 복사해서 만든다 (스냅샷). 나중에 라우팅이 바뀌어도 이미 내린 작업지시는 그대로다.
 */
@Entity
@Table(name = "work_order_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrderOperation {

    public enum Status { WAIT, RUN, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long woOpId;

    @ManyToOne(fetch = FetchType.LAZY) // M : 1 , 필요 한순간에만 가져오기 위해 FetchType을 LAZY로
    @JoinColumn(name = "wo_no") // 외래키 설정
    private WorkOrder workOrder;

    private Integer seq;

    private String processCode;

    private String wcCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Status status;

    private Integer goodQty;

    private Integer defectQty;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    static WorkOrderOperation fromRouting(WorkOrder workOrder, Routing routing) {
        WorkOrderOperation op = new WorkOrderOperation();
        op.workOrder = workOrder;
        op.seq = routing.getSeq();
        op.processCode = routing.getProcessCode();
        op.wcCode = routing.getWcCode();
        op.status = Status.WAIT;
        op.goodQty = 0;
        op.defectQty = 0;
        return op;
    }

    /** 대기 → 진행. 작업지시가 아직 대기면 함께 진행으로 바꾼다 (첫 공정을 시작하는 순간이 작업지시 시작). */
    public void start(LocalDateTime now) {
        requireStatus(Status.WAIT, "시작");
        if (workOrder.getStatus() == WorkOrderStatus.WAIT) {
            workOrder.start(now);
        }
        workOrder.requireAcceptingResults();
        status = Status.RUN;
        startedAt = now;
    }

    /** 실적 등록: 진행 중일 때만 받는다. */
    public void addResult(int good, int defect) {
        requireStatus(Status.RUN, "실적 등록");
        workOrder.requireAcceptingResults();
        goodQty += good;
        defectQty += defect;
    }

    /** 실적 취소: 등록했던 수량을 뺀다. 공정이 끝났어도 작업지시가 마감 전이면 취소할 수 있다. */
    public void removeResult(int good, int defect) {
        workOrder.requireNotClosed();
        goodQty -= good;
        defectQty -= defect;
    }

    /** 진행 → 완료. 마지막 공정까지 모두 끝나면 작업지시도 완료된다. */
    public void complete(LocalDateTime now) {
        requireStatus(Status.RUN, "완료");
        status = Status.DONE;
        endedAt = now;
        workOrder.completeIfAllOperationsDone(now);
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "%s 상태의 공정은 %s할 수 없습니다. (%s 상태에서만 가능)".formatted(status, action, expected));
        }
    }
}
