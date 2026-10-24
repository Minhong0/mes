package com.minimes.quality.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.repository.LotHistoryRepository;
import com.minimes.lot.repository.LotRepository;
import com.minimes.lot.service.LotHoldService;
import com.minimes.lot.service.LotHoldService.HoldResult;
import com.minimes.master.domain.InspectionSpec;
import com.minimes.master.repository.InspectionSpecRepository;
import com.minimes.master.service.MasterQueryService;
import com.minimes.quality.domain.InspectionResult;
import com.minimes.quality.domain.Judgement;
import com.minimes.quality.repository.InspectionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LOT 검사: 그 LOT을 만든 공정의 검사 항목을 시료 여러 개로 재고, 서버가 하한·상한으로 합격(OK)·불합격(NG)을 정한다.
 *
 * 측정값 하나라도 NG면 LOT이 불합격이고, 그 자리에서 LOT과 하위 LOT을 보류(HOLD)한다.
 * LOT 하나는 공정마다 한 번 검사한다. 같은 값을 다시 보내면(응답을 못 받은 POP의 재전송) 처음 결과를 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InspectionService {

    public static final int MAX_SAMPLES = 10;

    private final LotRepository lotRepository;
    private final LotHistoryRepository historyRepository;
    private final InspectionSpecRepository specRepository;
    private final InspectionResultRepository resultRepository;
    private final LotHoldService holdService;
    private final MasterQueryService masterQueryService;
    private final Clock clock;

    /** 검사표: LOT을 만든 공정의 검사 항목과, 이미 검사했으면 그 결과 */
    @Transactional(readOnly = true)
    public InspectionSheet sheet(String lotNo) {
        Lot lot = lotRepository.findById(lotNo).orElseThrow(() -> lotNotFound(lotNo));
        return sheetOf(lot, specsOf(lot), null);
    }

    public InspectionSheet inspect(String lotNo, InspectCommand command) {
        masterQueryService.getActiveWorker(command.workerId());
        // 같은 LOT을 두 단말이 동시에 검사하지 않도록 잠근다. 불합격이면 보류도 이 LOT을 잠근다
        Lot lot = lotRepository.findForUpdate(List.of(lotNo)).stream().findFirst().orElseThrow(() -> lotNotFound(lotNo));
        if (lot.getStatus() == LotStatus.SCRAPPED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "폐기된 LOT은 검사할 수 없습니다: " + lotNo);
        }
        List<InspectionSpec> specs = specsOf(lot);
        if (specs.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이 LOT을 만든 공정에는 검사 항목이 없습니다: " + lotNo);
        }
        Map<String, BigDecimal> values = checkComplete(specs, command.measurements());

        List<InspectionResult> existing = resultRepository
                .findByLotNoAndProcessCodeOrderByInspectCodeAscSampleNoAsc(lotNo, lot.getLastProcess());
        if (!existing.isEmpty()) {
            if (sameValues(existing, values)) {
                return sheetOf(lot, specs, null);   // 같은 검사를 다시 보냈다
            }
            throw new BusinessException(ErrorCode.INSPECTION_DONE,
                    "이 LOT은 이미 검사했습니다: %s (%s)".formatted(lotNo, lot.getLastProcess()));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, InspectionSpec> specByCode = specs.stream()
                .collect(Collectors.toMap(InspectionSpec::getInspectCode, Function.identity()));
        List<InspectionResult> results = resultRepository.saveAll(command.measurements().stream()
                .map(m -> InspectionResult.measure(lotNo, specByCode.get(m.inspectCode()), m.sampleNo(), m.value(),
                        command.workerId(), now))
                .toList());
        List<InspectionResult> ng = results.stream().filter(r -> r.getJudgement() == Judgement.NG).toList();
        int samples = results.stream().mapToInt(InspectionResult::getSampleNo).max().orElseThrow();

        String note = ng.isEmpty()
                ? "합격 (시료 %d개, 측정 %d개)".formatted(samples, results.size())
                : "불합격: " + ngSummary(ng, specByCode);
        historyRepository.save(LotHistory.of(lotNo, LotEventType.INSPECT, lot.getQty(), lot.getLastProcess(), null,
                command.workerId(), null, note, now));
        HoldResult hold = ng.isEmpty() ? null : holdService.holdByRule(lotNo, command.workerId(), cut("검사 " + note));
        return sheetOf(lot, specs, hold);
    }

    private List<InspectionSpec> specsOf(Lot lot) {
        return lot.getLastProcess() == null
                ? List.of()   // 입고한 원자재: 이 화면에서는 검사하지 않는다
                : specRepository.findByProcessCodeOrderByInspectCode(lot.getLastProcess());
    }

    /**
     * 모든 항목을 시료 1번부터 n번까지 빠짐없이 쟀는가. n은 보낸 시료 번호 중 가장 큰 것.
     * 돌려주는 값: "항목#시료" → 측정값
     */
    private static Map<String, BigDecimal> checkComplete(List<InspectionSpec> specs, List<Measurement> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "측정값이 없습니다.");
        }
        Map<String, InspectionSpec> specByCode = specs.stream()
                .collect(Collectors.toMap(InspectionSpec::getInspectCode, Function.identity()));
        Map<String, BigDecimal> values = new HashMap<>();
        int samples = 0;
        for (Measurement m : measurements) {
            if (!specByCode.containsKey(m.inspectCode())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이 공정의 검사 항목이 아닙니다: " + m.inspectCode());
            }
            if (m.sampleNo() < 1 || m.sampleNo() > MAX_SAMPLES) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "시료 번호는 1~%d입니다: %d".formatted(MAX_SAMPLES, m.sampleNo()));
            }
            if (values.put(key(m.inspectCode(), m.sampleNo()), m.value()) != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "같은 항목·시료를 두 번 적었습니다: %s #%d".formatted(m.inspectCode(), m.sampleNo()));
            }
            samples = Math.max(samples, m.sampleNo());
        }
        List<String> missing = new ArrayList<>();
        for (InspectionSpec spec : specs) {
            for (int s = 1; s <= samples; s++) {
                if (!values.containsKey(key(spec.getInspectCode(), s))) {
                    missing.add("%s #%d".formatted(spec.getInspectName(), s));
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "모든 항목을 시료 1~%d번까지 재야 합니다. 빠진 것: %s".formatted(samples, String.join(", ", missing)));
        }
        return values;
    }

    private static boolean sameValues(List<InspectionResult> existing, Map<String, BigDecimal> values) {
        return existing.size() == values.size() && existing.stream().allMatch(r -> {
            BigDecimal sent = values.get(key(r.getInspectCode(), r.getSampleNo()));
            return sent != null && sent.compareTo(r.getMeasuredValue()) == 0;
        });
    }

    /** 예: "외경 #2 38.07 (상한 38.05) 외 1건". 사유 칸(150자)에 들어가도록 첫 건만 자세히 쓴다 */
    private static String ngSummary(List<InspectionResult> ng, Map<String, InspectionSpec> specByCode) {
        InspectionResult first = ng.getFirst();
        InspectionSpec spec = specByCode.get(first.getInspectCode());
        String limit = spec.getUsl() != null && first.getMeasuredValue().compareTo(spec.getUsl()) > 0
                ? "상한 " + plain(spec.getUsl())
                : "하한 " + plain(spec.getLsl());
        String text = "%s #%d %s (%s)".formatted(spec.getInspectName(), first.getSampleNo(), plain(first.getMeasuredValue()), limit);
        return ng.size() == 1 ? text : "%s 외 %d건".formatted(text, ng.size() - 1);
    }

    private InspectionSheet sheetOf(Lot lot, List<InspectionSpec> specs, HoldResult hold) {
        String processName = lot.getLastProcess() == null ? null
                : masterQueryService.processMap().get(lot.getLastProcess()).getProcessName();
        List<InspectionResult> results = lot.getLastProcess() == null ? List.of()
                : resultRepository.findByLotNoAndProcessCodeOrderByInspectCodeAscSampleNoAsc(lot.getLotNo(), lot.getLastProcess());
        return new InspectionSheet(lot, processName, specs, results, hold);
    }

    /** 보류 사유는 150자까지 (lot_history.note 200자에 원인 LOT 번호가 붙는다) */
    private static String cut(String reason) {
        return reason.length() > 150 ? reason.substring(0, 150) : reason;
    }

    private static String key(String inspectCode, int sampleNo) {
        return inspectCode + "#" + sampleNo;
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static BusinessException lotNotFound(String lotNo) {
        return new BusinessException(ErrorCode.LOT_NOT_FOUND, "LOT을 찾을 수 없습니다: " + lotNo);
    }

    public record InspectCommand(String workerId, List<Measurement> measurements) {
    }

    public record Measurement(String inspectCode, int sampleNo, BigDecimal value) {
    }

    /**
     * @param results 이 공정에서 잰 측정값 (아직 검사 전이면 비어 있다)
     * @param hold    이번 검사가 불합격이라 보류한 결과. 합격이거나 조회면 null
     */
    public record InspectionSheet(Lot lot, String processName, List<InspectionSpec> specs, List<InspectionResult> results,
                                  HoldResult hold) {

        /** 아직 검사 전이면 null, 측정값 하나라도 NG면 NG */
        public Judgement judgement() {
            if (results.isEmpty()) {
                return null;
            }
            return results.stream().anyMatch(r -> r.getJudgement() == Judgement.NG) ? Judgement.NG : Judgement.OK;
        }

        public int sampleCount() {
            return results.stream().mapToInt(InspectionResult::getSampleNo).max().orElse(0);
        }
    }
}
