package com.minimes.workorder.web;

import com.minimes.master.service.MasterQueryService;
import com.minimes.workorder.domain.WorkOrder;
import com.minimes.workorder.domain.WorkOrderStatus;
import com.minimes.workorder.service.WorkOrderService;
import com.minimes.workorder.web.WorkOrderDtos.CreateRequest;
import com.minimes.workorder.web.WorkOrderDtos.WorkOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 작업지시 API. 상태 변경은 동작 이름(start, complete, close)으로 나뉜다. */
@Tag(name = "작업지시", description = "작업지시 생성, 조회, 상태 변경 (대기 → 진행 → 완료 → 마감)")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final MasterQueryService masterQueryService;

    @Operation(summary = "작업지시 생성", description = "품목의 라우팅을 따라 공정별 작업이 함께 만들어진다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderResponse create(@Valid @RequestBody CreateRequest req) {
        return response(workOrderService.create(req.itemCode(), req.planQty(), req.dueDate(), req.note()));
    }

    @Operation(summary = "작업지시 목록", description = "status를 주면 해당 상태만 조회 (WAIT, RUN, DONE, CLOSED)")
    @GetMapping
    public List<WorkOrderResponse> list(@RequestParam(required = false) WorkOrderStatus status) {
        Map<String, String> processNames = masterQueryService.processNames();
        return workOrderService.findAll(status).stream().map(wo -> WorkOrderResponse.from(wo, processNames)).toList();
    }

    @Operation(summary = "작업지시 단건")
    @GetMapping("/{woNo}")
    public WorkOrderResponse get(@PathVariable String woNo) {
        return response(workOrderService.get(woNo));
    }

    @Operation(summary = "작업 시작 (대기 → 진행)")
    @PostMapping("/{woNo}/start")
    public WorkOrderResponse start(@PathVariable String woNo) {
        return response(workOrderService.start(woNo));
    }

    @Operation(summary = "작업 완료 (진행 → 완료)")
    @PostMapping("/{woNo}/complete")
    public WorkOrderResponse complete(@PathVariable String woNo) {
        return response(workOrderService.complete(woNo));
    }

    @Operation(summary = "마감 (완료 → 마감)")
    @PostMapping("/{woNo}/close")
    public WorkOrderResponse close(@PathVariable String woNo) {
        return response(workOrderService.close(woNo));
    }

    private WorkOrderResponse response(WorkOrder wo) {
        return WorkOrderResponse.from(wo, masterQueryService.processNames());
    }
}
