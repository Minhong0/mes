package com.minimes.workorder.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.service.LotInput;
import com.minimes.lot.service.LotService;
import com.minimes.lot.service.LotService.InputCandidates;
import com.minimes.lot.service.LotService.InputPlan;
import com.minimes.master.domain.CommonCodeId;
import com.minimes.master.domain.Equipment;
import com.minimes.master.repository.CommonCodeRepository;
import com.minimes.master.repository.EquipmentRepository;
import com.minimes.master.service.MasterQueryService;
import com.minimes.workorder.domain.WorkOrderOperation;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.domain.WorkResult.DefectLine;
import com.minimes.workorder.mapper.OperationItem;
import com.minimes.workorder.mapper.WorkListMapper;
import com.minimes.workorder.repository.WorkOrderOperationRepository;
import com.minimes.workorder.repository.WorkResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * POP가 하는 일: 공정 시작, 실적 등록, 공정 완료, 실적 취소.
 * 상태 규칙(진행 중에만 실적, 마감 후 취소 불가 등)은 엔티티가 맡고, 여기서는 입력 확인과 순서를 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OperationService {

    private static final String DEFECT_TYPE = "DEFECT_TYPE";

    private final WorkOrderOperationRepository operationRepository;
    private final WorkResultRepository resultRepository;
    private final WorkListMapper workListMapper;
    private final EquipmentRepository equipmentRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final MasterQueryService masterQueryService;
    private final LotService lotService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<OperationItem> openOperations(String wcCode) {
        return workListMapper.selectOpenOperations(wcCode);
    }

    public WorkOrderOperation start(long woOpId, String workerId) {
        masterQueryService.getActiveWorker(workerId);
        WorkOrderOperation op = getOperation(woOpId);
        op.start(LocalDateTime.now(clock));
        return op;
    }

    /**
     * 실적 등록. 같은 requestId가 이미 있으면 새로 저장하지 않고 그때 저장한 실적을 돌려준다.
     * POP가 응답을 받지 못해 다시 보낸 경우다.
     * 투입 LOT을 줄이고 양품으로 산출 LOT을 만드는 일도 같은 트랜잭션에서 한다. 하나라도 틀리면 실적도 저장되지 않는다.
     */
    public WorkResult report(long woOpId, ReportCommand command) {
        Optional<WorkResult> already = resultRepository.findByRequestId(command.requestId());
        if (already.isPresent()) {
            return already.get();
        }
        masterQueryService.getActiveWorker(command.workerId());
        WorkOrderOperation op = getOperation(woOpId);
        checkEquipment(op, command.equipCode());
        command.defects().forEach(line -> checkDefectType(line.defectType()));

        LocalDateTime now = LocalDateTime.now(clock);
        // 이 실적이 덮는 구간의 시작: 앞 실적이 끝난 때, 첫 실적이면 공정을 시작한 때
        LocalDateTime startedAt = resultRepository.findTopByWoOpIdAndCancelledFalseOrderByEndedAtDesc(woOpId)
                .map(WorkResult::getEndedAt)
                .orElse(op.getStartedAt());
        WorkResult result = WorkResult.fromPop(command.requestId(), woOpId, command.equipCode(), command.workerId(),
                command.goodQty(), command.defects(), startedAt, now);
        op.addResult(result.getGoodQty(), result.getDefectQty());
        InputPlan plan = lotService.planInputs(op, result.getGoodQty() + result.getDefectQty(), command.inputs());
        resultRepository.save(result);   // IDENTITY라 여기서 INSERT하고 result_id를 받는다. LOT 이력이 이 번호를 쓴다
        lotService.record(plan, result);
        publishChanged(result);
        return result;
    }

    public WorkOrderOperation complete(long woOpId, String workerId) {
        masterQueryService.getActiveWorker(workerId);
        WorkOrderOperation op = getOperation(woOpId);
        op.complete(LocalDateTime.now(clock));
        return op;
    }

    /** 실적 취소: 취소 표시를 하고 공정 합계에서 뺀다. 만든 LOT은 폐기하고 투입한 LOT에는 수량을 돌려준다. */
    public WorkResult cancel(long resultId, String workerId, String reason) {
        masterQueryService.getActiveWorker(workerId);
        WorkResult result = resultRepository.findWithDefectsByResultId(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND, "실적을 찾을 수 없습니다: " + resultId));
        WorkOrderOperation op = getOperation(result.getWoOpId());
        LocalDateTime now = LocalDateTime.now(clock);
        result.cancel(workerId, reason, now);
        op.removeResult(result.getGoodQty(), result.getDefectQty());
        lotService.cancel(result, workerId, now);
        publishChanged(result);
        return result;
    }

    /** 이 공정에 투입할 품목과 쓸 수 있는 LOT (POP 투입 LOT 선택) */
    @Transactional(readOnly = true)
    public List<InputCandidates> inputCandidates(long woOpId) {
        return lotService.inputCandidates(getOperation(woOpId));
    }

    @Transactional(readOnly = true)
    public List<WorkResult> results(long woOpId) {
        getOperation(woOpId);
        return resultRepository.findByWoOpIdOrderByResultIdDesc(woOpId);
    }

    private WorkOrderOperation getOperation(long woOpId) {
        return operationRepository.findById(woOpId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_NOT_FOUND, "공정 작업을 찾을 수 없습니다: " + woOpId));
    }

    /** 설비를 적은 실적만 일별 집계(설비별)에 들어간다. */
    private void publishChanged(WorkResult result) {
        if (result.getEquipCode() != null) {
            eventPublisher.publishEvent(new WorkResultChangedEvent(result.getEquipCode(), result.getEndedAt().toLocalDate()));
        }
    }

    /** 설비는 비워도 되지만, 적었다면 그 공정의 작업장 설비여야 한다. */
    private void checkEquipment(WorkOrderOperation op, String equipCode) {
        if (equipCode == null) {
            return;
        }
        Equipment equipment = equipmentRepository.findById(equipCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "설비를 찾을 수 없습니다: " + equipCode));
        if (!op.getWcCode().equals(equipment.getWcCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "%s는 %s 작업장 설비가 아닙니다.".formatted(equipCode, op.getWcCode()));
        }
    }

    private void checkDefectType(String defectType) {
        if (!commonCodeRepository.existsById(new CommonCodeId(DEFECT_TYPE, defectType))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 불량 유형입니다: " + defectType);
        }
    }

    /** 실적 등록 요청. inputs: 투입한 LOT과 수량 */
    public record ReportCommand(String requestId, String workerId, String equipCode, int goodQty,
                                List<DefectLine> defects, List<LotInput> inputs) {
    }
}
