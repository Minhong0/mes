package com.minimes.workorder.domain;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 생산 실적 한 건. 테이블: work_result (불량 내역은 defect)
 *
 * 실적은 고치지 않는다. 잘못 입력했으면 취소 표시를 하고 새로 등록한다.
 */
@Entity
@Table(name = "work_result", uniqueConstraints = @UniqueConstraint(name = "uq_work_result_request", columnNames = "request_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkResult {

    public static final String SOURCE_POP = "POP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long resultId;

    private Long woOpId;

    private String equipCode;

    private String workerId;

    private LocalDateTime startedAt;        // 이 실적이 덮는 작업 구간: 앞 실적이 끝난 때(없으면 공정 시작)부터

    private LocalDateTime endedAt;          // 등록한 때까지

    private int goodQty;

    private int defectQty;

    private String source;

    private boolean cancelled;

    private LocalDateTime cancelledAt;

    private String cancelledBy;

    private String cancelReason;

    private LocalDateTime createdAt;

    private String createdBy;

    private String requestId;               // POP가 붙인 요청 ID. 같은 요청을 다시 보내도 한 번만 저장된다

    @OneToMany(mappedBy = "workResult", cascade = CascadeType.ALL)
    private List<Defect> defects = new ArrayList<>();

    /** POP 실적. 불량 수량은 불량 내역의 합이다. */
    public static WorkResult fromPop(String requestId, long woOpId, String equipCode, String workerId, int goodQty,
                                     List<DefectLine> defectLines, LocalDateTime startedAt, LocalDateTime now) {
        WorkResult result = new WorkResult();
        result.requestId = requestId;
        result.woOpId = woOpId;
        result.equipCode = equipCode;
        result.workerId = workerId;
        result.goodQty = goodQty;
        result.startedAt = startedAt;
        result.endedAt = now;
        result.source = SOURCE_POP;
        result.createdAt = now;
        result.createdBy = workerId;
        defectLines.forEach(line -> result.defects.add(Defect.of(result, line.defectType(), line.qty(), now)));
        result.defectQty = defectLines.stream().mapToInt(DefectLine::qty).sum();
        if (result.goodQty + result.defectQty <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "양품 또는 불량 수량을 1개 이상 입력해야 합니다.");
        }
        return result;
    }

    /** 취소 표시. 행은 지우지 않는다. */
    public void cancel(String by, String reason, LocalDateTime now) {
        if (cancelled) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 취소한 실적입니다.");
        }
        cancelled = true;
        cancelledAt = now;
        cancelledBy = by;
        cancelReason = reason;
    }

    public List<Defect> getDefects() {
        return Collections.unmodifiableList(defects);
    }

    /** 불량 한 줄: 유형(공통코드 DEFECT_TYPE)과 수량 */
    public record DefectLine(String defectType, int qty) {
    }
}
