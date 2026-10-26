package com.minimes.alarm.domain;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 설비 알람. 테이블: equipment_alarm
 * 담당자가 확인(ack)하기 전까지 열린 알람이다. 같은 설비·종류의 열린 알람이 있으면 새로 만들지 않는다.
 */
@Entity
@Table(name = "equipment_alarm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alarmId;

    private String equipCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AlarmType alarmType;

    private BigDecimal measuredValue;

    private BigDecimal limitValue;

    private String toolCode;

    private LocalDateTime occurredAt;       // 측정 시각 (공장 시계)

    private LocalDateTime ackAt;            // null이면 아직 확인 전

    private String ackBy;

    /** 측정값이 관리한계를 넘었다. */
    public static EquipmentAlarm limitExceeded(String equipCode, AlarmType alarmType, BigDecimal measuredValue,
                                               BigDecimal limitValue, LocalDateTime occurredAt) {
        EquipmentAlarm alarm = new EquipmentAlarm();
        alarm.equipCode = equipCode;
        alarm.alarmType = alarmType;
        alarm.measuredValue = measuredValue;
        alarm.limitValue = limitValue;
        alarm.occurredAt = occurredAt;
        return alarm;
    }

    /** 공구가 수명(교체 기준 수량)에 이르렀다. measuredValue = 사용 수량, limitValue = 수명 */
    public static EquipmentAlarm toolLifeReached(String equipCode, String toolCode, int usedCount, int lifeCount,
                                                 LocalDateTime occurredAt) {
        EquipmentAlarm alarm = new EquipmentAlarm();
        alarm.equipCode = equipCode;
        alarm.alarmType = AlarmType.TOOL_LIFE;
        alarm.toolCode = toolCode;
        alarm.measuredValue = BigDecimal.valueOf(usedCount);
        alarm.limitValue = BigDecimal.valueOf(lifeCount);
        alarm.occurredAt = occurredAt;
        return alarm;
    }

    public boolean isOpen() {
        return ackAt == null;
    }

    /** 담당자가 확인했다. 이미 확인한 알람을 다시 확인할 수는 없다. */
    public void acknowledge(String by, LocalDateTime at) {
        if (!isOpen()) {
            throw new BusinessException(ErrorCode.ALARM_ALREADY_ACKNOWLEDGED);
        }
        this.ackBy = by;
        this.ackAt = at;
    }
}
