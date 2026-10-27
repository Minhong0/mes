package com.minimes.lot.web;

import com.minimes.auth.config.LoginWorker;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.service.LotHoldService;
import com.minimes.lot.service.LotHoldService.HoldCommand;
import com.minimes.lot.service.LotService;
import com.minimes.master.domain.Item;
import com.minimes.master.service.MasterQueryService;
import com.minimes.lot.service.LotService.ReceiveCommand;
import com.minimes.lot.web.LotDtos.HeldLotResponse;
import com.minimes.lot.web.LotDtos.HoldRequest;
import com.minimes.lot.web.LotDtos.HoldResponse;
import com.minimes.lot.web.LotDtos.InputLotsResponse;
import com.minimes.lot.web.LotDtos.LotDetailResponse;
import com.minimes.lot.web.LotDtos.LotResponse;
import com.minimes.lot.web.LotDtos.ReceiveRequest;
import com.minimes.lot.web.LotDtos.TraceResponse;
import com.minimes.workorder.service.OperationService;
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
import java.util.stream.Collectors;

@Tag(name = "LOT 추적", description = "원자재 입고, LOT 조회·이력, 정방향·역방향 추적, 품질 보류·해제, 공정 투입 LOT")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;
    private final LotHoldService holdService;
    private final MasterQueryService masterQueryService;
    private final OperationService operationService;

    @Operation(summary = "원자재 입고", description = "LOT 관리 원자재(환봉, 키홈 핀)를 입고해 LOT을 만든다. LOT 번호: 품번-yyMMdd-001")
    @PostMapping("/lots/receive")
    @ResponseStatus(HttpStatus.CREATED)
    public LotResponse receive(@Valid @RequestBody ReceiveRequest req, @LoginWorker String workerId) {
        String supplierLotNo = req.supplierLotNo() == null || req.supplierLotNo().isBlank() ? null : req.supplierLotNo().trim();
        return LotResponse.from(lotService.receive(new ReceiveCommand(req.itemCode(), req.qty(), supplierLotNo, workerId)));
    }

    @Operation(summary = "품목의 LOT 목록", description = "최근 것부터 100개. status를 주면 그 상태만 (ACTIVE, CONSUMED ...)")
    @GetMapping("/lots")
    public List<LotResponse> list(@RequestParam String itemCode, @RequestParam(required = false) LotStatus status) {
        return lotService.findByItem(itemCode, status).stream().map(LotResponse::from).toList();
    }

    @Operation(summary = "보류 중인 LOT", description = "지금 HOLD인 LOT과 마지막 보류(때, 누가, 사유, 원인 LOT, 검사 불합격인가). 최근에 보류한 것부터")
    @GetMapping("/lots/held")
    public List<HeldLotResponse> held() {
        Map<String, String> itemNames = masterQueryService.findItems(null).stream()
                .collect(Collectors.toMap(Item::getItemCode, Item::getItemName));
        Map<String, String> processNames = masterQueryService.processNames();
        return holdService.heldLots().stream().map(h -> HeldLotResponse.from(h, itemNames, processNames)).toList();
    }

    @Operation(summary = "LOT과 이력", description = "입고·생산·투입·취소·보류 이력을 일어난 순서대로")
    @GetMapping("/lots/{lotNo}")
    public LotDetailResponse detail(@PathVariable String lotNo) {
        return LotDetailResponse.from(lotService.detail(lotNo));
    }

    @Operation(summary = "LOT 추적", description = "backward: 이 LOT을 만든 재료(원자재까지), forward: 이 LOT이 들어간 곳(완제품까지). 취소한 실적은 뺀다")
    @GetMapping("/lots/{lotNo}/trace")
    public TraceResponse trace(@PathVariable String lotNo) {
        return TraceResponse.from(lotService.trace(lotNo));
    }

    @Operation(summary = "품질 보류", description = "남은 수량이 있는 LOT을 보류해 투입을 막는다. includeDownstream이면 이 LOT이 들어간 하위 LOT도 함께. 품질(QC)·관리자(ADMIN)만")
    @PostMapping("/lots/{lotNo}/hold")
    public HoldResponse hold(@PathVariable String lotNo, @Valid @RequestBody HoldRequest req,
                             @LoginWorker String workerId) {
        return HoldResponse.from(holdService.hold(lotNo, command(req, workerId)));
    }

    @Operation(summary = "보류 해제", description = "보류 중인 LOT을 다시 쓸 수 있게 한다. includeDownstream이면 하위 LOT도 함께. 품질(QC)·관리자(ADMIN)만")
    @PostMapping("/lots/{lotNo}/release")
    public HoldResponse release(@PathVariable String lotNo, @Valid @RequestBody HoldRequest req,
                                @LoginWorker String workerId) {
        return HoldResponse.from(holdService.release(lotNo, command(req, workerId)));
    }

    private static HoldCommand command(HoldRequest req, String workerId) {
        return new HoldCommand(workerId, req.reason().trim(), Boolean.TRUE.equals(req.includeDownstream()));
    }

    @Operation(summary = "공정에 투입할 LOT (POP)", description = "투입할 품목마다 쓸 수 있는 LOT, 먼저 들어온 것부터")
    @GetMapping("/operations/{woOpId}/input-lots")
    public List<InputLotsResponse> inputLots(@PathVariable long woOpId) {
        return operationService.inputCandidates(woOpId).stream().map(InputLotsResponse::from).toList();
    }
}
