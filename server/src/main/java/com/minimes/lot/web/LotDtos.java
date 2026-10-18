package com.minimes.lot.web;

import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.mapper.TraceNode;
import com.minimes.lot.service.LotService.InputCandidates;
import com.minimes.lot.service.LotService.LotDetail;
import com.minimes.lot.service.LotService.Trace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** LOT API의 요청·응답 형식. */
public final class LotDtos {

    private LotDtos() {
    }

    public record ReceiveRequest(
            @NotBlank(message = "품목은 필수입니다.") String itemCode,
            @NotNull @Positive(message = "수량은 0보다 커야 합니다.") BigDecimal qty,
            @Size(max = 30) String supplierLotNo,
            @NotBlank(message = "작업자는 필수입니다.") String workerId
    ) {
    }

    public record LotResponse(String lotNo, String itemCode, BigDecimal qty, BigDecimal initialQty, String status,
                              String woNo, String lastProcess, String supplierLotNo, LocalDateTime createdAt,
                              String createdBy) {
        public static LotResponse from(Lot l) {
            return new LotResponse(l.getLotNo(), l.getItemCode(), l.getQty(), l.getInitialQty(), l.getStatus().name(),
                    l.getWoNo(), l.getLastProcess(), l.getSupplierLotNo(), l.getCreatedAt(), l.getCreatedBy());
        }
    }

    public record HistoryResponse(String eventType, String processCode, BigDecimal qty, String equipCode,
                                  String workerId, Long resultId, String note, LocalDateTime eventAt) {
        public static HistoryResponse from(LotHistory h) {
            return new HistoryResponse(h.getEventType().name(), h.getProcessCode(), h.getQty(), h.getEquipCode(),
                    h.getWorkerId(), h.getResultId(), h.getNote(), h.getEventAt());
        }
    }

    public record LotDetailResponse(LotResponse lot, String itemName, List<HistoryResponse> history) {
        public static LotDetailResponse from(LotDetail d) {
            return new LotDetailResponse(LotResponse.from(d.lot()), d.itemName(),
                    d.history().stream().map(HistoryResponse::from).toList());
        }
    }

    /** backward: 이 LOT을 만든 재료 쪽 (원자재까지), forward: 이 LOT이 들어간 쪽 (완제품까지) */
    public record TraceResponse(LotResponse lot, List<TraceNode> backward, List<TraceNode> forward) {
        public static TraceResponse from(Trace t) {
            return new TraceResponse(LotResponse.from(t.lot()), t.backward(), t.forward());
        }
    }

    /**
     * 공정에 투입할 품목 하나와 쓸 수 있는 LOT (먼저 들어온 것부터).
     *
     * @param qtyPer      산출 1개(양품 + 불량)당 투입 수량
     * @param fromProcess 앞 공정. null이면 입고한 원자재
     */
    public record InputLotsResponse(String itemCode, String itemName, BigDecimal qtyPer, String fromProcess,
                                    List<LotStock> lots) {
        public static InputLotsResponse from(InputCandidates c) {
            return new InputLotsResponse(c.required().itemCode(), c.itemName(), c.required().qtyPer(),
                    c.required().fromProcess(), c.lots().stream().map(LotStock::from).toList());
        }
    }

    public record LotStock(String lotNo, BigDecimal qty, LocalDateTime createdAt) {
        public static LotStock from(Lot l) {
            return new LotStock(l.getLotNo(), l.getQty(), l.getCreatedAt());
        }
    }
}
