package com.minimes.master.web;

import com.minimes.master.domain.CommonCode;
import com.minimes.master.domain.Equipment;
import com.minimes.master.domain.Item;
import com.minimes.master.domain.ManufacturingProcess;
import com.minimes.master.domain.Routing;

import java.math.BigDecimal;

/**
 * 기준정보 API 응답 형식.
 * 엔티티를 그대로 내보내지 않고 응답용 record로 바꾼다. 엔티티 필드가 바뀌어도 API 형식이 흔들리지 않게 하기 위해서다.
 */
public final class MasterResponses {

    private MasterResponses() {
    }

    public record ItemResponse(String itemCode, String itemName, String itemType, String spec, String unit) {
        public static ItemResponse from(Item i) {
            return new ItemResponse(i.getItemCode(), i.getItemName(), i.getItemType().name(), i.getSpec(), i.getUnit());
        }
    }

    public record RoutingResponse(int seq, String processCode, String processName, String wcCode,
                                  String outputItemCode, BigDecimal stdTimeMin, String timeBasis,
                                  Integer batchMaxQty, BigDecimal setupTimeMin) {
        public static RoutingResponse from(Routing r, ManufacturingProcess p) {
            return new RoutingResponse(r.getSeq(), r.getProcessCode(), p == null ? null : p.getProcessName(),
                    r.getWcCode(), r.getOutputItemCode(), r.getStdTimeMin(), r.getTimeBasis().name(),
                    r.getBatchMaxQty(), r.getSetupTimeMin());
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
}
