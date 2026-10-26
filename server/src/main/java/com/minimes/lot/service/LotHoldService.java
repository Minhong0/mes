package com.minimes.lot.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotEventType;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.mapper.LotTraceMapper;
import com.minimes.lot.mapper.TraceNode;
import com.minimes.lot.repository.LotHistoryRepository;
import com.minimes.lot.repository.LotRepository;
import com.minimes.master.domain.Worker;
import com.minimes.master.service.MasterQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 품질 보류(HOLD)와 해제(RELEASE). 문제 LOT 하나만, 또는 그 LOT이 들어간 하위 LOT(정방향 추적)까지 한꺼번에 바꾼다.
 *
 * 보류는 남은 수량이 있는(ACTIVE) LOT만 바꾸고, 다 쓴·폐기·출하 LOT은 건너뛴 목록으로 돌려준다.
 * 바꾼 LOT마다 lot_history에 HOLD·RELEASE를 남긴다. 품질(QC)·관리자(ADMIN)만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LotHoldService {

    /** 검사 불합격 자동 보류의 사유 머리말. 보류 목록이 이 말로 원인을 가른다 */
    public static final String INSPECTION_FAILED = "검사 불합격";

    private static final Set<String> ALLOWED_ROLES = Set.of("QC", "ADMIN");
    private static final int MAX_TRACE_ROUNDS = 10;
    private static final String ORIGIN_NOTE = "%s (원인 LOT %s)";
    private static final Pattern ORIGIN_PATTERN = Pattern.compile("^(.*) \\(원인 LOT ([^)]+)\\)$", Pattern.DOTALL);

    private final LotRepository lotRepository;
    private final LotHistoryRepository historyRepository;
    private final LotTraceMapper traceMapper;
    private final MasterQueryService masterQueryService;
    private final Clock clock;

    public HoldResult hold(String lotNo, HoldCommand command) {
        checkAllowed(command.workerId());
        return holdTargets(lotNo, command);
    }

    /**
     * 규칙에 따른 자동 보류 (검사 불합격). 사람이 판단하는 보류가 아니라 역할을 보지 않고, 하위 LOT까지 막는다.
     * 해제는 여전히 품질 담당·관리자만 한다.
     */
    public HoldResult holdByRule(String lotNo, String workerId, String reason) {
        return holdTargets(lotNo, new HoldCommand(workerId, reason, true));
    }

    private HoldResult holdTargets(String lotNo, HoldCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Lot> changed = new ArrayList<>();
        List<Lot> skipped = new ArrayList<>();
        for (Lot lot : lockTargets(lotNo, command.includeDownstream())) {
            if (!lot.canHold()) {
                skipped.add(lot);
                continue;
            }
            lot.hold();
            historyRepository.save(LotHistory.of(lot.getLotNo(), LotEventType.HOLD, lot.getQty(), null, null,
                    command.workerId(), null, note(command.reason(), lotNo, lot), now));
            changed.add(lot);
        }
        return new HoldResult(lotNo, changed, skipped);
    }

    public HoldResult release(String lotNo, HoldCommand command) {
        checkAllowed(command.workerId());
        LocalDateTime now = LocalDateTime.now(clock);
        List<Lot> changed = new ArrayList<>();
        List<Lot> skipped = new ArrayList<>();
        for (Lot lot : lockTargets(lotNo, command.includeDownstream())) {
            if (lot.getStatus() != LotStatus.HOLD) {
                skipped.add(lot);
                continue;
            }
            lot.release();
            historyRepository.save(LotHistory.of(lot.getLotNo(), LotEventType.RELEASE, lot.getQty(), null, null,
                    command.workerId(), null, note(command.reason(), lotNo, lot), now));
            changed.add(lot);
        }
        return new HoldResult(lotNo, changed, skipped);
    }

    /** 지금 보류 중인 LOT과 마지막 보류 이력 (최근에 보류한 것부터) */
    @Transactional(readOnly = true)
    public List<HeldLot> heldLots() {
        List<Lot> lots = lotRepository.findByStatus(LotStatus.HOLD);
        Map<String, LotHistory> lastHold = new HashMap<>();
        if (!lots.isEmpty()) {
            historyRepository.findByLotNoInAndEventTypeOrderByHistoryIdDesc(lots.stream().map(Lot::getLotNo).toList(),
                    LotEventType.HOLD).forEach(h -> lastHold.putIfAbsent(h.getLotNo(), h));
        }
        return lots.stream()
                .map(lot -> HeldLot.of(lot, lastHold.get(lot.getLotNo())))
                .sorted(Comparator.comparing(HeldLot::heldAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(h -> h.lot().getLotNo()))
                .toList();
    }

    private void checkAllowed(String workerId) {
        Worker worker = masterQueryService.getActiveWorker(workerId);
        if (!ALLOWED_ROLES.contains(worker.getRole())) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "LOT 보류·해제는 품질 담당이나 관리자만 할 수 있습니다: " + workerId);
        }
    }

    /**
     * 바꿀 LOT을 잠그고 읽는다 (시작 LOT, 그다음 정방향 추적 순서).
     * 추적한 뒤 잠그기 전에 다른 실적이 하위 LOT을 새로 만들 수 있다. 그 실적은 투입 LOT을 잠그므로,
     * 잠금을 얻은 뒤 다시 추적하면 그 사이 커밋된 LOT이 보인다. 새로 나온 LOT이 없을 때까지 반복한다.
     */
    private List<Lot> lockTargets(String lotNo, boolean includeDownstream) {
        Map<String, Lot> locked = new HashMap<>();
        Set<String> order = new LinkedHashSet<>(List.of(lotNo));
        Set<String> toLock = Set.of(lotNo);
        for (int round = 0; !toLock.isEmpty(); round++) {
            if (round == MAX_TRACE_ROUNDS) {
                throw new IllegalStateException("하위 LOT이 계속 늘어나 보류 대상을 정하지 못했습니다: " + lotNo);
            }
            lotRepository.findForUpdate(toLock).forEach(lot -> locked.put(lot.getLotNo(), lot));
            if (!locked.containsKey(lotNo)) {
                throw new BusinessException(ErrorCode.LOT_NOT_FOUND, "LOT을 찾을 수 없습니다: " + lotNo);
            }
            if (!includeDownstream) {
                break;
            }
            Set<String> found = new LinkedHashSet<>();
            for (TraceNode node : traceMapper.selectForward(lotNo)) {
                if (order.add(node.lotNo())) {
                    found.add(node.lotNo());
                }
            }
            toLock = found;
        }
        return order.stream().map(locked::get).toList();
    }

    /** 하위 LOT에는 어느 LOT 때문에 바뀌었는지 함께 적는다. 사유는 150자까지라 note(200자)에 들어간다. */
    private static String note(String reason, String rootLotNo, Lot lot) {
        return lot.getLotNo().equals(rootLotNo) ? reason : ORIGIN_NOTE.formatted(reason, rootLotNo);
    }

    /** @param includeDownstream true면 이 LOT이 들어간 하위 LOT(정방향 추적)도 함께 */
    public record HoldCommand(String workerId, String reason, boolean includeDownstream) {
    }

    /**
     * @param changed 상태를 바꾼 LOT (정방향 추적 순서)
     * @param skipped 바꾸지 않은 LOT. 보류: ACTIVE가 아닌 것 (다 씀, 폐기, 출하, 이미 보류), 해제: 보류가 아닌 것
     */
    public record HoldResult(String lotNo, List<Lot> changed, List<Lot> skipped) {
    }

    /**
     * 보류 중인 LOT 하나.
     *
     * @param originLotNo  보류를 건 LOT (하위로 함께 보류됐으면 원인 LOT, 아니면 이 LOT)
     * @param byInspection 검사 불합격으로 자동 보류됐는가
     */
    public record HeldLot(Lot lot, LocalDateTime heldAt, String heldBy, String reason, String originLotNo,
                          boolean byInspection) {

        /** 이력의 메모 "사유 (원인 LOT X)"를 사유와 원인 LOT으로 나눈다. 보류 이력이 없으면(DB를 직접 고친 경우) 빈 칸 */
        static HeldLot of(Lot lot, LotHistory hold) {
            if (hold == null) {
                return new HeldLot(lot, null, null, null, lot.getLotNo(), false);
            }
            String reason = hold.getNote() == null ? "" : hold.getNote();
            String origin = lot.getLotNo();
            Matcher m = ORIGIN_PATTERN.matcher(reason);
            if (m.matches()) {
                reason = m.group(1);
                origin = m.group(2);
            }
            return new HeldLot(lot, hold.getEventAt(), hold.getWorkerId(), reason, origin, reason.startsWith(INSPECTION_FAILED));
        }
    }
}
