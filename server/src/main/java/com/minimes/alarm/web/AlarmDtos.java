package com.minimes.alarm.web;

import com.minimes.alarm.domain.AlarmType;
import com.minimes.alarm.domain.EquipmentAlarm;
import com.minimes.alarm.domain.EquipmentLimit;
import com.minimes.alarm.domain.LimitMetric;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 알람 API의 요청·응답. 엔티티를 그대로 내보내지 않고, 화면에 필요한 값만 담는다. */
public final class AlarmDtos {

    private AlarmDtos() {
    }

    /** @param ackBy 확인한 사람 (로그인이 없어서 화면이 정해 보낸다). 비우면 "UNKNOWN" */
    public record AckRequest(@Size(max = 20) String ackBy) {
    }

    public record AlarmResponse(long alarmId, String equipCode, AlarmType alarmType, BigDecimal measuredValue,
                                BigDecimal limitValue, LocalDateTime occurredAt, LocalDateTime ackAt, String ackBy) {

        public static AlarmResponse from(EquipmentAlarm a) {
            return new AlarmResponse(a.getAlarmId(), a.getEquipCode(), a.getAlarmType(), a.getMeasuredValue(),
                    a.getLimitValue(), a.getOccurredAt(), a.getAckAt(), a.getAckBy());
        }
    }

    public record LimitResponse(String equipCode, LimitMetric metric, BigDecimal centerValue, BigDecimal sigma,
                                BigDecimal ucl, int sampleCount, LocalDateTime baseFrom, LocalDateTime baseTo,
                                LocalDateTime calculatedAt) {

        public static LimitResponse from(EquipmentLimit l) {
            return new LimitResponse(l.getEquipCode(), l.getMetric(), l.getCenterValue(), l.getSigma(), l.getUcl(),
                    l.getSampleCount(), l.getBaseFrom(), l.getBaseTo(), l.getCalculatedAt());
        }
    }
}
