package com.minimes.equipment.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.equipment.domain.Downtime;
import com.minimes.equipment.domain.DowntimeSource;
import com.minimes.equipment.repository.DowntimeRepository;
import com.minimes.master.domain.CommonCode;
import com.minimes.master.domain.CommonCodeId;
import com.minimes.master.repository.CommonCodeRepository;
import com.minimes.master.repository.EquipmentRepository;
import com.minimes.master.service.MasterQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * POP 정지 사유 입력: 설비가 남긴 정지 목록을 보여 주고, 작업자가 사유(대분류·소분류)를 확정한다.
 * 확정한 사유는 같은 downtime 행에 들어가므로 모니터링 화면과 정지 사유 파레토가 바로 그 사유로 바뀐다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DowntimeService {

    static final String DT_CAT = "DT_CAT";
    static final String DT_SUB = "DT_SUB";
    static final int MAX_RECENT = 50;

    private final DowntimeRepository downtimeRepository;
    private final EquipmentRepository equipmentRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final MasterQueryService masterQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 설비의 최근 정지 (설비가 자동으로 남긴 것, 최근 것부터) */
    @Transactional(readOnly = true)
    public List<Downtime> recent(String equipCode, int limit) {
        if (!equipmentRepository.existsById(equipCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "설비를 찾을 수 없습니다: " + equipCode);
        }
        int size = Math.clamp(limit, 1, MAX_RECENT);
        return downtimeRepository.findByEquipCodeAndSourceOrderByStartedAtDesc(equipCode, DowntimeSource.EQUIP, Limit.of(size));
    }

    /**
     * 정지 사유 확정. 소분류는 비워도 되지만, 적었다면 그 대분류에 속한 소분류여야 한다.
     * 같은 내용으로 몇 번을 보내도 결과가 같다 (POP가 응답을 못 받아 다시 보내도 괜찮다).
     */
    public Downtime confirmReason(long downtimeId, ReasonCommand command) {
        masterQueryService.getActiveWorker(command.workerId());
        Downtime downtime = downtimeRepository.findById(downtimeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOWNTIME_NOT_FOUND, "정지 기록을 찾을 수 없습니다: " + downtimeId));
        if (!commonCodeRepository.existsById(new CommonCodeId(DT_CAT, command.dtCat()))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 정지 대분류입니다: " + command.dtCat());
        }
        if (command.dtSub() != null) {
            CommonCode sub = commonCodeRepository.findById(new CommonCodeId(DT_SUB, command.dtSub()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 정지 소분류입니다: " + command.dtSub()));
            if (!command.dtCat().equals(sub.getParentCode())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "%s(%s)는 %s 대분류의 소분류가 아닙니다.".formatted(sub.getCodeName(), sub.getCode(), command.dtCat()));
            }
        }
        downtime.confirmReason(command.dtCat(), command.dtSub(), command.note(), command.workerId(), LocalDateTime.now(clock));
        eventPublisher.publishEvent(new DowntimeReasonChangedEvent(downtime.getEquipCode(), downtime.getDowntimeId()));
        return downtime;
    }

    /** 정지 사유 확정 요청. dtSub, note는 비울 수 있다 */
    public record ReasonCommand(String workerId, String dtCat, String dtSub, String note) {
    }
}
