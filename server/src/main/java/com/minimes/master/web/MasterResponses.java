package com.minimes.master.web;

import com.minimes.master.domain.CommonCode;
import com.minimes.master.domain.Worker;
import com.minimes.master.domain.Equipment;
import com.minimes.master.domain.Item;
import com.minimes.master.domain.ManufacturingProcess;
import com.minimes.master.domain.Routing;

import java.math.BigDecimal;

/** 기준정보 API 응답 형식. */
public final class MasterResponses {

    private MasterResponses() {
    }

    public record ItemResponse(String itemCode, String itemName, String itemType, String spec, String unit,
                               boolean lotTracked) {
        public static ItemResponse from(Item i) {
            return new ItemResponse(i.getItemCode(), i.getItemName(), i.getItemType().name(), i.getSpec(), i.getUnit(),
                    i.isLotTracked());
        }
    }

    public record RoutingResponse(int seq, String processCode, String processName, String wcCode,
                                  String outputItemCode, BigDecimal stdTimeMin, String timeBasis,
                                  Integer batchMaxQty, BigDecimal setupTimeMin, Integer lotSize) {
        public static RoutingResponse from(Routing r, ManufacturingProcess p) {
            return new RoutingResponse(r.getSeq(), r.getProcessCode(), p == null ? null : p.getProcessName(),
                    r.getWcCode(), r.getOutputItemCode(), r.getStdTimeMin(), r.getTimeBasis().name(),
                    r.getBatchMaxQty(), r.getSetupTimeMin(), r.getLotSize());
        }
    }

    public record EquipmentResponse(String equipCode, String equipName, String wcCode, String equipType,
                                    BigDecimal idealCtMin, boolean autoCollect, String status) {
        public static EquipmentResponse from(Equipment e) {
            return new EquipmentResponse(e.getEquipCode(), e.getEquipName(), e.getWcCode(), e.getEquipType(),
                    e.getIdealCtMin(), e.isAutoCollect(), e.getStatus());
        }
    }

    public record CodeResponse(String code, String codeName, String parentCode) {
        public static CodeResponse from(CommonCode c) {
            return new CodeResponse(c.getCode(), c.getCodeName(), c.getParentCode());
        }
    }

    public record WorkerResponse(String workerId, String workerName, String wcCode, String role) {
        public static WorkerResponse from(Worker w) {
            return new WorkerResponse(w.getWorkerId(), w.getWorkerName(), w.getWcCode(), w.getRole());
        }
    }
}
