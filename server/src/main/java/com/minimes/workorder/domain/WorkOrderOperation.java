package com.minimes.workorder.domain;

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
}
