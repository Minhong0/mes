package com.minimes.lot.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotGenealogy;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.mapper.LotTraceMapper;
import com.minimes.lot.mapper.TraceNode;
import com.minimes.lot.repository.LotGenealogyRepository;
import com.minimes.lot.repository.LotHistoryRepository;
import com.minimes.lot.repository.LotRepository;
import com.minimes.master.domain.Bom;
import com.minimes.master.domain.Item;
import com.minimes.master.domain.ItemType;
import com.minimes.master.domain.RoutingId;
import com.minimes.master.repository.BomRepository;
import com.minimes.master.repository.RoutingRepository;
import com.minimes.master.service.MasterQueryService;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderOperation;
import com.minimes.workorder.domain.WorkResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LOT 입고, 실적의 투입·산출, 실적 취소 되돌리기.
 *
 * 실적 하나는 투입 LOT의 수량을 줄이고(CONSUME), 양품 수량으로 새 LOT을 만들고(PRODUCE),
 * 투입 → 산출을 계보(lot_genealogy)에 잇는다. 이 계보를 따라가면 완제품 LOT에서 원자재 LOT까지 거슬러 올라갈 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LotService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int LOT_NO_MAX_LENGTH = 30;
    private static final List<LotEventType> RESULT_EVENTS = List.of(LotEventType.PRODUCE, LotEventType.CONSUME);

    private final LotRepository lotRepository;
    private final LotHistoryRepository historyRepository;
    private final LotGenealogyRepository genealogyRepository;
    private final RoutingRepository routingRepository;
    private final BomRepository bomRepository;
    private final LotTraceMapper traceMapper;
    private final MasterQueryService masterQueryService;
    private final Clock clock;

    /** 원자재 입고: LOT 관리 원자재만 받는다. */
    public Lot receive(ReceiveCommand command) {
        masterQueryService.getActiveWorker(command.workerId());
        Item item = masterQueryService.getItem(command.itemCode());
        if (item.getItemType() != ItemType.RM || !item.isLotTracked()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "LOT으로 입고하는 원자재가 아닙니다: " + item.getItemCode());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Lot lot = lotRepository.save(Lot.receive(nextLotNo(item.getItemCode(), now.toLocalDate()), item.getItemCode(),
                command.qty(), command.supplierLotNo(), command.workerId(), now));
        historyRepository.save(LotHistory.of(lot.getLotNo(), LotEventType.RECEIVE, lot.getQty(), null, null,
                command.workerId(), null, command.supplierLotNo() == null ? null : "공급사 LOT " + command.supplierLotNo(), now));
        return lot;
    }

    /**
     * 공정에 투입할 LOT 관리 품목.
     * 1) 앞 공정이 있으면 앞 공정의 산출 품목 (1개당 1개)
     * 2) 이 공정에서 BOM으로 들어가는 LOT 관리 자재 (예: 선삭의 환봉, 조립의 키홈 핀)
     * 절삭유, 연삭석 같은 소모품(lot_yn = N)은 고르지 않는다.
     */
    @Transactional(readOnly = true)
    public List<RequiredInput> requiredInputs(WorkOrderOperation op) {
        WorkOrder wo = op.getWorkOrder();
        List<RequiredInput> required = new ArrayList<>();
        String prevItem = wo.getOperations().stream()
                .filter(o -> o.getSeq() < op.getSeq())
                .max(Comparator.comparing(WorkOrderOperation::getSeq))
                .map(prev -> {
                    String item = outputItemOf(wo, prev.getSeq());
                    required.add(new RequiredInput(item, BigDecimal.ONE, prev.getProcessCode()));
                    return item;
                })
                .orElse(null);
        for (Bom bom : bomRepository.findByParentItemCodeAndProcessCode(outputItemOf(wo, op.getSeq()), op.getProcessCode())) {
            if (!bom.getChildItemCode().equals(prevItem) && masterQueryService.getItem(bom.getChildItemCode()).isLotTracked()) {
                required.add(new RequiredInput(bom.getChildItemCode(), bom.getQty(), null));
            }
        }
        return required;
    }

    /** POP 투입 LOT 선택 화면: 품목마다 쓸 수 있는 LOT (먼저 들어온 것부터) */
    @Transactional(readOnly = true)
    public List<InputCandidates> inputCandidates(WorkOrderOperation op) {
        String woNo = op.getWorkOrder().getWoNo();
        return requiredInputs(op).stream()
                .map(r -> new InputCandidates(r, masterQueryService.getItem(r.itemCode()).getItemName(),
                        r.fromProcess() == null
                                ? lotRepository.findByItemCodeAndStatusAndWoNoIsNullOrderByCreatedAtAscLotNoAsc(
                                        r.itemCode(), LotStatus.ACTIVE)
                                : lotRepository.findByItemCodeAndStatusAndWoNoAndLastProcessOrderByCreatedAtAscLotNoAsc(
                                        r.itemCode(), LotStatus.ACTIVE, woNo, r.fromProcess())))
                .toList();
    }

    /**
     * 실적의 투입 LOT을 확인한다. 저장 전에 불러서, 틀리면 실적을 저장하지 않는다.
     * 품목마다 투입 수량의 합이 (양품 + 불량) × 1개당 투입 수량과 같아야 한다.
     * 투입할 LOT은 잠그고 읽어(UPDLOCK) 다른 실적이 같은 LOT을 동시에 줄이지 못하게 한다.
     */
    public InputPlan planInputs(WorkOrderOperation op, int units, List<LotInput> inputs) {
        List<RequiredInput> required = requiredInputs(op);
        String woNo = op.getWorkOrder().getWoNo();
        Set<String> seen = new HashSet<>();
        for (LotInput input : inputs) {
            if (input.qty() == null || input.qty().signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "투입 수량은 0보다 커야 합니다: " + input.lotNo());
            }
            if (!seen.add(input.lotNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 LOT을 두 번 적었습니다: " + input.lotNo());
            }
        }
        Map<String, Lot> lots = lotRepository.findForUpdate(seen).stream()
                .collect(Collectors.toMap(Lot::getLotNo, Function.identity()));

        Map<String, BigDecimal> sumByItem = new HashMap<>();
        List<PlannedInput> planned = new ArrayList<>();
        for (LotInput input : inputs) {
            Lot lot = lots.get(input.lotNo());
            if (lot == null) {
                throw new BusinessException(ErrorCode.LOT_NOT_FOUND, "LOT을 찾을 수 없습니다: " + input.lotNo());
            }
            RequiredInput need = required.stream().filter(r -> r.itemCode().equals(lot.getItemCode())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                            "LOT %s(%s)는 이 공정에 투입하는 품목이 아닙니다.".formatted(lot.getLotNo(), lot.getItemCode())));
            if (!need.accepts(lot, woNo)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, need.fromProcess() == null
                        ? "LOT %s는 입고한 원자재 LOT이 아닙니다.".formatted(lot.getLotNo())
                        : "LOT %s는 이 작업지시의 %s 공정에서 만든 LOT이 아닙니다.".formatted(lot.getLotNo(), need.fromProcess()));
            }
            if (lot.getStatus() != LotStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                        "%s 상태의 LOT은 투입할 수 없습니다: %s".formatted(lot.getStatus(), lot.getLotNo()));
            }
            if (input.qty().compareTo(lot.getQty()) > 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "LOT %s의 남은 수량(%s)보다 많이 투입할 수 없습니다."
                        .formatted(lot.getLotNo(), plain(lot.getQty())));
            }
            planned.add(new PlannedInput(lot, input.qty()));
            sumByItem.merge(lot.getItemCode(), input.qty(), BigDecimal::add);
        }
        for (RequiredInput r : required) {
            BigDecimal needQty = r.qtyPer().multiply(BigDecimal.valueOf(units));
            BigDecimal given = sumByItem.getOrDefault(r.itemCode(), BigDecimal.ZERO);
            if (given.compareTo(needQty) != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "%s 투입 수량이 맞지 않습니다. (필요 %s, 입력 %s)"
                        .formatted(r.itemCode(), plain(needQty), plain(given)));
            }
        }
        return new InputPlan(op, outputItemOf(op.getWorkOrder(), op.getSeq()), planned);
    }

    /** 저장한 실적으로 투입 LOT을 줄이고, 양품이 있으면 산출 LOT을 만들어 계보에 잇는다. */
    public ResultLots record(InputPlan plan, WorkResult result) {
        WorkOrderOperation op = plan.operation();
        LocalDateTime now = result.getEndedAt();
        for (PlannedInput p : plan.inputs()) {
            p.lot().consume(p.qty());
            historyRepository.save(LotHistory.of(p.lot().getLotNo(), LotEventType.CONSUME, p.qty(), op.getProcessCode(),
                    result.getEquipCode(), result.getWorkerId(), result.getResultId(), null, now));
        }
        List<LotInput> inputs = plan.inputs().stream().map(p -> new LotInput(p.lot().getLotNo(), p.qty())).toList();
        if (result.getGoodQty() == 0) {
            return new ResultLots(null, inputs);   // 모두 불량: 투입만 하고 만든 LOT은 없다
        }
        Lot output = lotRepository.save(Lot.produce(nextLotNo(plan.outputItemCode(), now.toLocalDate()),
                plan.outputItemCode(), BigDecimal.valueOf(result.getGoodQty()), op.getWorkOrder().getWoNo(),
                op.getProcessCode(), result.getWorkerId(), now));
        historyRepository.save(LotHistory.of(output.getLotNo(), LotEventType.PRODUCE, output.getQty(), op.getProcessCode(),
                result.getEquipCode(), result.getWorkerId(), result.getResultId(), null, now));
        for (PlannedInput p : plan.inputs()) {
            genealogyRepository.save(LotGenealogy.link(p.lot().getLotNo(), output.getLotNo(), p.qty(), op.getProcessCode(),
                    result.getResultId(), now));
        }
        return new ResultLots(output.getLotNo(), inputs);
    }

    /**
     * 실적 취소: 만든 LOT을 폐기하고 투입 수량을 돌려준다. 계보는 지우지 않는다 (추적 쿼리가 취소한 실적을 뺀다).
     * 만든 LOT을 이미 다음 공정에 썼으면 취소할 수 없다 (LOT_IN_USE).
     */
    public void cancel(WorkResult result, String workerId, LocalDateTime now) {
        List<LotHistory> events = historyRepository.findByResultIdInAndEventTypeInOrderByHistoryId(
                List.of(result.getResultId()), RESULT_EVENTS);
        if (events.isEmpty()) {
            return;   // LOT 추적을 시작하기 전에 등록한 실적
        }
        Map<String, Lot> lots = lotRepository.findForUpdate(events.stream().map(LotHistory::getLotNo).toList()).stream()
                .collect(Collectors.toMap(Lot::getLotNo, Function.identity()));
        String note = "실적 #%d 취소".formatted(result.getResultId());
        // 만든 LOT부터 확인한다. 쓸 수 없으면 아무것도 바꾸기 전에 멈춘다
        for (LotHistory e : events) {
            if (e.getEventType() == LotEventType.PRODUCE) {
                lots.get(e.getLotNo()).cancelProduced();
                historyRepository.save(LotHistory.of(e.getLotNo(), LotEventType.CANCEL, e.getQty().negate(),
                        e.getProcessCode(), null, workerId, result.getResultId(), note + ": 만든 LOT 폐기", now));
            }
        }
        for (LotHistory e : events) {
            if (e.getEventType() == LotEventType.CONSUME) {
                lots.get(e.getLotNo()).restore(e.getQty());
                historyRepository.save(LotHistory.of(e.getLotNo(), LotEventType.CANCEL, e.getQty(),
                        e.getProcessCode(), null, workerId, result.getResultId(), note + ": 투입 수량 되돌림", now));
            }
        }
    }

    /** 실적 목록 응답에 붙일 LOT. LOT이 없는 실적은 결과에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, ResultLots> resultLots(Collection<Long> resultIds) {
        if (resultIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> outputs = new HashMap<>();
        Map<Long, List<LotInput>> inputs = new LinkedHashMap<>();
        for (LotHistory e : historyRepository.findByResultIdInAndEventTypeInOrderByHistoryId(resultIds, RESULT_EVENTS)) {
            if (e.getEventType() == LotEventType.PRODUCE) {
                outputs.put(e.getResultId(), e.getLotNo());
            } else {
                inputs.computeIfAbsent(e.getResultId(), id -> new ArrayList<>()).add(new LotInput(e.getLotNo(), e.getQty()));
            }
        }
        Map<Long, ResultLots> lots = new HashMap<>();
        Set<Long> ids = new HashSet<>(outputs.keySet());
        ids.addAll(inputs.keySet());
        ids.forEach(id -> lots.put(id, new ResultLots(outputs.get(id), inputs.getOrDefault(id, List.of()))));
        return lots;
    }

    @Transactional(readOnly = true)
    public Lot get(String lotNo) {
        return lotRepository.findById(lotNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOT_NOT_FOUND, "LOT을 찾을 수 없습니다: " + lotNo));
    }

    /** LOT 하나와 그 이력 (일어난 순서대로) */
    @Transactional(readOnly = true)
    public LotDetail detail(String lotNo) {
        Lot lot = get(lotNo);
        return new LotDetail(lot, masterQueryService.getItem(lot.getItemCode()).getItemName(),
                historyRepository.findByLotNoOrderByEventAtAscHistoryIdAsc(lotNo));
    }

    /** 역방향(어디서 왔나)과 정방향(어디로 갔나)을 함께 */
    @Transactional(readOnly = true)
    public Trace trace(String lotNo) {
        Lot lot = get(lotNo);
        return new Trace(lot, traceMapper.selectBackward(lotNo), traceMapper.selectForward(lotNo));
    }

    @Transactional(readOnly = true)
    public List<Lot> findByItem(String itemCode, LotStatus status) {
        masterQueryService.getItem(itemCode);
        return status == null
                ? lotRepository.findTop100ByItemCodeOrderByCreatedAtDescLotNoDesc(itemCode)
                : lotRepository.findTop100ByItemCodeAndStatusOrderByCreatedAtDescLotNoDesc(itemCode, status);
    }

    /**
     * LOT 번호: 품번-yyMMdd-001 (품목별, 그날의 일련번호). 예: SF-SFT-010-261018-003
     * 작업지시 번호와 같은 한계가 있다. 같은 순간에 같은 번호가 나오면 기본키 중복으로 저장이 실패하고,
     * POP는 같은 요청 ID로 다시 보내면 된다.
     */
    String nextLotNo(String itemCode, LocalDate date) {
        String prefix = itemCode + "-" + date.format(NO_DATE) + "-";
        int next = lotRepository.findTopByLotNoStartingWithOrderByLotNoDesc(prefix)
                .map(last -> Integer.parseInt(last.getLotNo().substring(prefix.length())) + 1)
                .orElse(1);
        String lotNo = prefix + "%03d".formatted(next);
        if (lotNo.length() > LOT_NO_MAX_LENGTH) {
            throw new IllegalStateException("LOT 번호가 너무 깁니다: " + lotNo);
        }
        return lotNo;
    }

    private String outputItemOf(WorkOrder wo, int seq) {
        return routingRepository.findById(new RoutingId(wo.getItemCode(), seq))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTING_NOT_FOUND,
                        "라우팅을 찾을 수 없습니다: %s %d".formatted(wo.getItemCode(), seq)))
                .getOutputItemCode();
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** 원자재 입고 요청 */
    public record ReceiveCommand(String itemCode, BigDecimal qty, String supplierLotNo, String workerId) {
    }

    /** 공정에 투입할 품목과 쓸 수 있는 LOT */
    public record InputCandidates(RequiredInput required, String itemName, List<Lot> lots) {
    }

    /** 확인을 마친 투입 계획. planInputs가 만들고 record가 쓴다. */
    public record InputPlan(WorkOrderOperation operation, String outputItemCode, List<PlannedInput> inputs) {
    }

    public record PlannedInput(Lot lot, BigDecimal qty) {
    }

    public record Trace(Lot lot, List<TraceNode> backward, List<TraceNode> forward) {
    }

    public record LotDetail(Lot lot, String itemName, List<LotHistory> history) {
    }
}
