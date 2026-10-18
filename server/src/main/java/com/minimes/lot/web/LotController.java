package com.minimes.lot.web;

import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.service.LotService;
import com.minimes.lot.service.LotService.ReceiveCommand;
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

@Tag(name = "LOT 추적", description = "원자재 입고, LOT 조회·이력, 정방향·역방향 추적, 공정 투입 LOT")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;
    private final OperationService operationService;

    @Operation(summary = "원자재 입고", description = "LOT 관리 원자재(환봉, 키홈 핀)를 입고해 LOT을 만든다. LOT 번호: 품번-yyMMdd-001")
    @PostMapping("/lots/receive")
    @ResponseStatus(HttpStatus.CREATED)
    public LotResponse receive(@Valid @RequestBody ReceiveRequest req) {
        String supplierLotNo = req.supplierLotNo() == null || req.supplierLotNo().isBlank() ? null : req.supplierLotNo().trim();
        return LotResponse.from(lotService.receive(new ReceiveCommand(req.itemCode(), req.qty(), supplierLotNo, req.workerId())));
    }

    @Operation(summary = "품목의 LOT 목록", description = "최근 것부터 100개. status를 주면 그 상태만 (ACTIVE, CONSUMED ...)")
    @GetMapping("/lots")
    public List<LotResponse> list(@RequestParam String itemCode, @RequestParam(required = false) LotStatus status) {
        return lotService.findByItem(itemCode, status).stream().map(LotResponse::from).toList();
    }

    @Operation(summary = "LOT과 이력", description = "입고·생산·투입·취소 이력을 일어난 순서대로")
    @GetMapping("/lots/{lotNo}")
    public LotDetailResponse detail(@PathVariable String lotNo) {
        return LotDetailResponse.from(lotService.detail(lotNo));
    }

    @Operation(summary = "LOT 추적", description = "backward: 이 LOT을 만든 재료(원자재까지), forward: 이 LOT이 들어간 곳(완제품까지). 취소한 실적은 뺀다")
    @GetMapping("/lots/{lotNo}/trace")
    public TraceResponse trace(@PathVariable String lotNo) {
        return TraceResponse.from(lotService.trace(lotNo));
    }

    @Operation(summary = "공정에 투입할 LOT (POP)", description = "투입할 품목마다 쓸 수 있는 LOT, 먼저 들어온 것부터")
    @GetMapping("/operations/{woOpId}/input-lots")
    public List<InputLotsResponse> inputLots(@PathVariable long woOpId) {
        return operationService.inputCandidates(woOpId).stream().map(InputLotsResponse::from).toList();
    }
}
