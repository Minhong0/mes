package com.minimes.alarm.domain;

import java.io.Serializable;

/** equipment_limit의 복합키 (설비, 항목). 필드 이름이 EquipmentLimit의 @Id 필드와 같아야 한다. */
public record EquipmentLimitId(String equipCode, LimitMetric metric) implements Serializable {

    public EquipmentLimitId() {
        this(null, null);
    }
}
