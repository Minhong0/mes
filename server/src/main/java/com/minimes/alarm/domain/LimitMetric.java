package com.minimes.alarm.domain;

import com.minimes.equipment.service.EquipmentDataSavedEvent;

import java.math.BigDecimal;
import java.util.function.Function;

/** 관리한계를 두는 센서 항목. 항목마다 메시지에서 값을 꺼내는 방법과, 넘었을 때 남기는 알람 종류가 다르다. */
public enum LimitMetric {

    VIBRATION(AlarmType.VIBRATION_UCL, EquipmentDataSavedEvent::spindleVibration),
    TEMP(AlarmType.TEMP_UCL, EquipmentDataSavedEvent::spindleTemp);

    private final AlarmType alarmType;
    private final Function<EquipmentDataSavedEvent, BigDecimal> reader;

    LimitMetric(AlarmType alarmType, Function<EquipmentDataSavedEvent, BigDecimal> reader) {
        this.alarmType = alarmType;
        this.reader = reader;
    }

    public AlarmType alarmType() {
        return alarmType;
    }

    /** 메시지의 측정값. 센서 값이 없으면 null */
    public BigDecimal valueOf(EquipmentDataSavedEvent event) {
        return reader.apply(event);
    }
}
