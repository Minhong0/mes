package com.minimes.lot.web;

import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.mapper.TraceNode;
import com.minimes.lot.service.LotHoldService.HoldResult;
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
import java.util.Map;

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

    /** 보류·해제 요청. includeDownstream: 이 LOT이 들어간 하위 LOT도 함께 (보내지 않으면 이 LOT만) */
    public record HoldRequest(
            @NotBlank(message = "작업자는 필수입니다.") String workerId,
            @NotBlank(message = "사유는 필수입니다.") @Size(max = 150, message = "사유는 150자까지입니다.") String reason,
            boolean includeDownstream
    ) {
    }

    /** changed: 상태를 바꾼 LOT, skipped: 바꾸지 않은 LOT (다 쓴·폐기·출하 LOT, 해제할 때는 보류가 아닌 LOT) */
    public record HoldResponse(String lotNo, List<LotResponse> changed, List<LotResponse> skipped) {
        public static HoldResponse from(HoldResult r) {
            return new HoldResponse(r.lotNo(), r.changed().stream().map(LotResponse::from).toList(),
                    r.skipped().stream().map(LotResponse::from).toList());
        }
    }

    public record LotResponse(String lotNo, String itemCode, BigDecimal qty, BigDecimal initialQty, String status,
                              String woNo, String lastProcess, String supplierLotNo, LocalDateTime createdAt,
                              String createdBy) {
        public static LotResponse from(Lot l) {
            return new LotResponse(l.getLotNo(), l.getItemCode(), l.getQty(), l.getInitialQty(), l.getStatus().name(),
                    l.getWoNo(), l.getLastProcess(), l.getSupplierLotNo(), l.getCreatedAt(), l.getCreatedBy());
        }
    }

    public record HistoryResponse(String eventType, String processCode, String processName, BigDecimal qty,
                                  String equipCode, String workerId, Long resultId, String note, LocalDateTime eventAt) {
        public static HistoryResponse from(LotHistory h, Map<String, String> processNames) {
            return new HistoryResponse(h.getEventType().name(), h.getProcessCode(),
                    h.getProcessCode() == null ? null : processNames.get(h.getProcessCode()), h.getQty(),
                    h.getEquipCode(), h.getWorkerId(), h.getResultId(), h.getNote(), h.getEventAt());
        }
    }

    /** lastProcessName: LOT을 만든 공정의 이름 (원자재 LOT은 null) */
    public record LotDetailResponse(LotResponse lot, String itemName, String lastProcessName,
                                    List<HistoryResponse> history) {
        public static LotDetailResponse from(LotDetail d) {
            Map<String, String> names = d.processNames();
            return new LotDetailResponse(LotResponse.from(d.lot()), d.itemName(),
                    d.lot().getLastProcess() == null ? null : names.get(d.lot().getLastProcess()),
                    d.history().stream().map(h -> HistoryResponse.from(h, names)).toList());
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
