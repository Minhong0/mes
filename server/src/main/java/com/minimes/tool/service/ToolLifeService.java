package com.minimes.tool.service;

import com.minimes.alarm.service.AlarmService;
import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.equipment.service.ProducedCounter;
import com.minimes.master.service.MasterQueryService;
import com.minimes.tool.domain.Tool;
import com.minimes.tool.domain.ToolChange;
import com.minimes.tool.repository.ToolChangeRepository;
import com.minimes.tool.repository.ToolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 공구 수명: 공구마다 설치한 뒤 설비가 가공한 수량을 세고, 수명에 이르면 알람을 낸다. 공구를 바꾸면 0부터 다시 센다.
 *
 * 사용 수량은 메시지가 올 때마다 1씩 더하지 않고, 설치 시각부터 지금까지의 카운트를 매번 다시 센다 (ProducedCounter).
 * 통신 장애 뒤에 메시지가 늦게, 순서가 바뀌어 도착해도 결과가 같고, 같은 메시지를 두 번 세는 일도 없다.
 *
 * 설치·교체 시각은 그 설비의 마지막 메시지 시각(공장 시계)이다. 설비 카운트가 공장 시계로 오므로 같은 시계로 잘라야 한다.
 * (시뮬레이터를 배속으로 돌리면 공장 시계가 서버 시계보다 앞서 간다.)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ToolLifeService {

    private final ToolRepository toolRepository;
    private final ToolChangeRepository changeRepository;
    private final EquipmentDataRepository equipmentDataRepository;
    private final ProducedCounter producedCounter;
    private final AlarmService alarmService;
    private final MasterQueryService masterQueryService;
    private final Clock clock;

    /** 모든 공구의 사용 수량을 다시 세고, 수명에 이른 공구는 알람을 낸다. 새로 낸 알람 수를 돌려준다. */
    public int refresh() {
        int raised = 0;
        for (Tool tool : toolRepository.findAllByOrderByEquipCodeAscToolCodeAsc()) {
            if (refresh(tool)) {
                raised++;
            }
        }
        return raised;
    }

    private boolean refresh(Tool tool) {
        Optional<LocalDateTime> machineNow = lastMachineTime(tool.getEquipCode());
        if (machineNow.isEmpty()) {
            return false;   // 설비 데이터가 아직 없다
        }
        if (!tool.isCounting()) {
            tool.install(machineNow.get());   // 처음 세는 공구: 지금부터 센다
            return false;
        }
        tool.updateUsed(producedCounter.countAfter(tool.getEquipCode(), tool.getInstalledAt()));
        if (!tool.isLifeReached()) {
            return false;
        }
        return alarmService.raiseToolLife(tool.getEquipCode(), tool.getToolCode(), tool.getUsedCount(), tool.getLifeCount(),
                tool.getInstalledAt(), machineNow.get()).isPresent();
    }

    /**
     * 공구를 바꿨다. 바꾼 공구가 몇 개를 가공했는지 이력에 남기고, 새 공구를 0개부터 센다.
     * 그 공구의 열린 수명 알람은 바꾼 사람이 확인한 것으로 닫는다.
     */
    public ChangeResult change(String toolCode, ChangeCommand command) {
        masterQueryService.getActiveWorker(command.workerId());
        Tool tool = get(toolCode);
        refresh(tool);   // 바꾸기 직전까지 센 수량을 이력에 남긴다
        LocalDateTime machineNow = lastMachineTime(tool.getEquipCode()).orElse(LocalDateTime.now(clock));
        ToolChange change = changeRepository.save(ToolChange.of(tool, machineNow, command.reason(), command.workerId()));
        tool.install(machineNow);
        alarmService.closeToolAlarms(toolCode, command.workerId());
        return new ChangeResult(tool, change);
    }

    @Transactional(readOnly = true)
    public List<Tool> findAll() {
        return toolRepository.findAllByOrderByEquipCodeAscToolCodeAsc();
    }

    /** 교체 이력 (최근 것부터 50건). toolCode가 null이면 모든 공구 */
    @Transactional(readOnly = true)
    public List<ToolChange> changes(String toolCode) {
        if (toolCode == null) {
            return changeRepository.findTop50ByOrderByChangeIdDesc();
        }
        get(toolCode);
        return changeRepository.findTop50ByToolCodeOrderByChangeIdDesc(toolCode);
    }

    private Tool get(String toolCode) {
        return toolRepository.findById(toolCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND, "공구를 찾을 수 없습니다: " + toolCode));
    }

    private Optional<LocalDateTime> lastMachineTime(String equipCode) {
        return equipmentDataRepository.findFirstByEquipCodeOrderByCollectedAtDesc(equipCode).map(EquipmentData::getCollectedAt);
    }

    /** tool: 새 공구 (0개부터), change: 남긴 이력 (바꾼 공구가 가공한 수량) */
    public record ChangeResult(Tool tool, ToolChange change) {
    }

    /** reason: 예) 수명 도달, 파손, 품질 문제 */
    public record ChangeCommand(String workerId, String reason) {
    }
}
