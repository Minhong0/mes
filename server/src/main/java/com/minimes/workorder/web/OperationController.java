package com.minimes.workorder.web;

import com.minimes.auth.config.LoginWorker;
import com.minimes.lot.service.LotService;
import com.minimes.master.service.MasterQueryService;
import com.minimes.lot.service.ResultLots;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.mapper.OperationItem;
import com.minimes.workorder.service.OperationService;
import com.minimes.workorder.service.OperationService.ReportCommand;
import com.minimes.workorder.web.OperationDtos.CancelRequest;
import com.minimes.workorder.web.OperationDtos.ReportRequest;
import com.minimes.workorder.web.OperationDtos.ResultResponse;
import com.minimes.workorder.web.WorkOrderDtos.OperationResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 현장 POP가 부르는 API. 누가 했는지는 로그인 토큰의 사번으로 남긴다. */
@Tag(name = "공정 작업 (POP)", description = "작업 목록, 공정 시작, 실적 등록·취소, 공정 완료")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OperationController {

    private final OperationService operationService;
    private final LotService lotService;
    private final MasterQueryService masterQueryService;

    @Operation(summary = "작업장의 작업 목록", description = "대기·진행 중인 공정. 진행 중인 것이 먼저, 그다음 납기 순")
    @GetMapping("/work-centers/{wcCode}/operations")
    public List<OperationItem> openOperations(@PathVariable String wcCode) {
        return operationService.openOperations(wcCode);
    }

    @Operation(summary = "공정 시작 (대기 → 진행)", description = "작업지시가 대기면 함께 진행으로 바뀐다")
    @PostMapping("/operations/{woOpId}/start")
    public OperationResponse start(@PathVariable long woOpId, @LoginWorker String workerId) {
        return OperationResponse.from(operationService.start(woOpId, workerId), masterQueryService.processNames());
    }

    @Operation(summary = "실적 등록", description = "양품 수량, 불량 내역, 투입 LOT. 양품으로 산출 LOT을 만든다. "
            + "같은 requestId로 다시 보내면 처음 저장한 실적을 돌려준다")
    @PostMapping("/operations/{woOpId}/results")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultResponse report(@PathVariable long woOpId, @Valid @RequestBody ReportRequest req,
                                 @LoginWorker String workerId) {
        ReportCommand command = new ReportCommand(req.requestId(), workerId,
                req.equipCode() == null || req.equipCode().isBlank() ? null : req.equipCode(),
                req.goodQty(), req.defectLines(), req.lotInputs());
        return withLots(List.of(operationService.report(woOpId, command))).getFirst();
    }

    @Operation(summary = "공정의 실적 목록", description = "최근 것부터. 취소한 실적도 표시와 함께 나온다")
    @GetMapping("/operations/{woOpId}/results")
    public List<ResultResponse> results(@PathVariable long woOpId) {
        return withLots(operationService.results(woOpId));
    }

    @Operation(summary = "공정 완료 (진행 → 완료)", description = "마지막 공정까지 끝나면 작업지시도 완료된다")
    @PostMapping("/operations/{woOpId}/complete")
    public OperationResponse complete(@PathVariable long woOpId, @LoginWorker String workerId) {
        return OperationResponse.from(operationService.complete(woOpId, workerId), masterQueryService.processNames());
    }

    @Operation(summary = "실적 취소", description = "행을 지우지 않고 취소 표시. 공정 합계에서 빠진다. 마감한 작업지시는 불가")
    @PostMapping("/results/{resultId}/cancel")
    public ResultResponse cancel(@PathVariable long resultId, @Valid @RequestBody CancelRequest req,
                                 @LoginWorker String workerId) {
        return withLots(List.of(operationService.cancel(resultId, workerId, req.reason()))).getFirst();
    }

    private List<ResultResponse> withLots(List<WorkResult> results) {
        Map<Long, ResultLots> lots = lotService.resultLots(results.stream().map(WorkResult::getResultId).toList());
        return results.stream()
                .map(r -> ResultResponse.from(r, lots.getOrDefault(r.getResultId(), ResultLots.NONE)))
                .toList();
    }
}
