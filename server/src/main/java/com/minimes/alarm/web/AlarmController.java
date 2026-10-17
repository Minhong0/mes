package com.minimes.alarm.web;

import com.minimes.alarm.service.AlarmService;
import com.minimes.alarm.service.LimitCalculationService;
import com.minimes.alarm.web.AlarmDtos.AckRequest;
import com.minimes.alarm.web.AlarmDtos.AlarmResponse;
import com.minimes.alarm.web.AlarmDtos.LimitResponse;
import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "설비 알람", description = "관리한계(UCL) 계산, 알람 조회와 확인")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;
    private final LimitCalculationService limitCalculationService;

    @Operation(summary = "알람 목록", description = "open=true면 확인 전 알람만, false면 최근 100건")
    @GetMapping("/alarms")
    public List<AlarmResponse> alarms(@RequestParam(defaultValue = "true") boolean open) {
        return alarmService.findAlarms(open).stream().map(AlarmResponse::from).toList();
    }

    @Operation(summary = "알람 확인", description = "확인한 알람은 대시보드에서 사라진다. 이미 확인했으면 409")
    @PostMapping("/alarms/{alarmId}/ack")
    public AlarmResponse acknowledge(@PathVariable long alarmId, @Valid @RequestBody(required = false) AckRequest req) {
        String ackBy = req == null || req.ackBy() == null || req.ackBy().isBlank() ? "UNKNOWN" : req.ackBy();
        return AlarmResponse.from(alarmService.acknowledge(alarmId, ackBy));
    }

    @Operation(summary = "관리한계 목록")
    @GetMapping("/equipment-limits")
    public List<LimitResponse> limits() {
        return limitCalculationService.findAll().stream().map(LimitResponse::from).toList();
    }

    @Operation(summary = "관리한계 계산",
            description = "from~to(측정 날짜, 둘 다 포함)의 가동 데이터로 UCL = 평균 + 3σ. 고장 징후가 없던 기간을 고른다")
    @PostMapping("/equipment-limits/calculate")
    public List<LimitResponse> calculate(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from이 to보다 늦습니다.");
        }
        return limitCalculationService.calculate(from, to).stream().map(LimitResponse::from).toList();
    }
}
