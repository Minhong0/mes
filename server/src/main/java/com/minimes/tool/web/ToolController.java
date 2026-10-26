package com.minimes.tool.web;

import com.minimes.master.domain.Equipment;
import com.minimes.master.service.MasterQueryService;
import com.minimes.tool.domain.Tool;
import com.minimes.tool.service.ToolLifeService;
import com.minimes.tool.service.ToolLifeService.ChangeCommand;
import com.minimes.tool.service.ToolLifeService.ChangeResult;
import com.minimes.tool.web.ToolDtos.ChangeRequest;
import com.minimes.tool.web.ToolDtos.ChangeResponse;
import com.minimes.tool.web.ToolDtos.ChangeResultResponse;
import com.minimes.tool.web.ToolDtos.ToolResponse;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "공구 수명", description = "공구별 사용 수량과 수명, 교체와 교체 이력")
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolLifeService toolLifeService;
    private final MasterQueryService masterQueryService;

    @Operation(summary = "공구 목록", description = "설치한 뒤 가공한 수량(30초마다 다시 셈), 수명, 남은 수량, 상태(OK, WARN 90% 이상, REPLACE 수명 도달)")
    @GetMapping
    public List<ToolResponse> list() {
        Map<String, String> equipNames = masterQueryService.findEquipment().stream()
                .collect(Collectors.toMap(Equipment::getEquipCode, Equipment::getEquipName));
        return toolLifeService.findAll().stream().map(t -> ToolResponse.from(t, equipNames.get(t.getEquipCode()))).toList();
    }

    @Operation(summary = "공구 교체", description = "바꾼 공구의 가공 수량을 이력에 남기고 새 공구를 0개부터 센다. 그 공구의 열린 수명 알람은 닫는다")
    @PostMapping("/{toolCode}/change")
    public ChangeResultResponse change(@PathVariable String toolCode, @Valid @RequestBody ChangeRequest req) {
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().trim();
        ChangeResult result = toolLifeService.change(toolCode, new ChangeCommand(req.workerId(), reason));
        Tool tool = result.tool();
        String equipName = masterQueryService.findEquipment().stream()
                .filter(e -> e.getEquipCode().equals(tool.getEquipCode())).map(Equipment::getEquipName).findFirst().orElse(null);
        return new ChangeResultResponse(ToolResponse.from(tool, equipName), ChangeResponse.from(result.change()));
    }

    @Operation(summary = "교체 이력", description = "최근 것부터 50건. toolCode를 주면 그 공구만")
    @GetMapping("/changes")
    public List<ChangeResponse> changes(@RequestParam(required = false) String toolCode) {
        return toolLifeService.changes(toolCode).stream().map(ChangeResponse::from).toList();
    }
}
