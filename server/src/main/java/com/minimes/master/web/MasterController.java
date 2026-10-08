package com.minimes.master.web;

import com.minimes.master.domain.ItemType;
import com.minimes.master.domain.ManufacturingProcess;
import com.minimes.master.mapper.BomNode;
import com.minimes.master.service.MasterQueryService;
import com.minimes.master.web.MasterResponses.CodeResponse;
import com.minimes.master.web.MasterResponses.EquipmentResponse;
import com.minimes.master.web.MasterResponses.ItemResponse;
import com.minimes.master.web.MasterResponses.RoutingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "기준정보", description = "품목, 라우팅, BOM, 설비, 공통코드 조회")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MasterController {

    private final MasterQueryService masterQueryService;

    @Operation(summary = "품목 목록", description = "type을 주면 FG(완제품), SF(반제품), RM(원자재) 중 하나만 조회")
    @GetMapping("/items")
    public List<ItemResponse> items(@RequestParam(required = false) ItemType type) {
        return masterQueryService.findItems(type).stream().map(ItemResponse::from).toList();
    }

    @Operation(summary = "품목 단건")
    @GetMapping("/items/{itemCode}")
    public ItemResponse item(@PathVariable String itemCode) {
        return ItemResponse.from(masterQueryService.getItem(itemCode));
    }

    @Operation(summary = "품목의 라우팅 (공정 순서)")
    @GetMapping("/items/{itemCode}/routings")
    public List<RoutingResponse> routings(@PathVariable String itemCode) {
        Map<String, ManufacturingProcess> processes = masterQueryService.processMap();
        return masterQueryService.findRoutings(itemCode).stream()
                .map(r -> RoutingResponse.from(r, processes.get(r.getProcessCode())))
                .toList();
    }

    @Operation(summary = "BOM 전개", description = "품목 1개를 만드는 데 필요한 하위 품목을 끝까지 펼친다 (재귀 CTE)")
    @GetMapping("/items/{itemCode}/bom-tree")
    public List<BomNode> bomTree(@PathVariable String itemCode) {
        return masterQueryService.findBomTree(itemCode);
    }

    @Operation(summary = "설비 목록")
    @GetMapping("/equipment")
    public List<EquipmentResponse> equipment() {
        return masterQueryService.findEquipment().stream().map(EquipmentResponse::from).toList();
    }

    @Operation(summary = "공통코드", description = "예: DT_CAT(비가동 대분류), DT_SUB(비가동 소분류), DEFECT_TYPE(불량 유형)")
    @GetMapping("/common-codes/{groupCode}")
    public List<CodeResponse> codes(@PathVariable String groupCode) {
        return masterQueryService.findCodes(groupCode).stream().map(CodeResponse::from).toList();
    }
}
