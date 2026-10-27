package com.minimes.equipment.web;

import com.minimes.auth.config.LoginWorker;
import com.minimes.equipment.service.DowntimeService;
import com.minimes.equipment.service.DowntimeService.ReasonCommand;
import com.minimes.equipment.web.DowntimeDtos.DowntimeResponse;
import com.minimes.equipment.web.DowntimeDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "정지 사유 (POP)", description = "설비가 남긴 정지 목록, 작업자의 정지 사유 확정")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DowntimeController {

    private final DowntimeService downtimeService;

    @Operation(summary = "설비의 최근 정지", description = "설비가 자동으로 남긴 정지, 최근 것부터 (최대 50건)")
    @GetMapping("/equipment/{equipCode}/downtimes")
    public List<DowntimeResponse> recent(@PathVariable String equipCode,
                                         @RequestParam(defaultValue = "10") int limit) {
        return downtimeService.recent(equipCode, limit).stream().map(DowntimeResponse::from).toList();
    }

    @Operation(summary = "정지 사유 확정", description = "대분류·소분류(DT_CAT, DT_SUB)를 작업자가 정한다. 같은 내용으로 다시 보내도 결과가 같다")
    @PutMapping("/downtimes/{downtimeId}/reason")
    public DowntimeResponse confirmReason(@PathVariable long downtimeId, @Valid @RequestBody ReasonRequest req,
                                          @LoginWorker String workerId) {
        ReasonCommand command = new ReasonCommand(workerId, req.dtCat(),
                req.dtSub() == null || req.dtSub().isBlank() ? null : req.dtSub(),
                req.note() == null || req.note().isBlank() ? null : req.note().trim());
        return DowntimeResponse.from(downtimeService.confirmReason(downtimeId, command));
    }
}
