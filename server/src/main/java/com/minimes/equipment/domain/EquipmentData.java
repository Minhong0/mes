package com.minimes.equipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 설비에서 MQTT로 받은 데이터 한 건 (원본 그대로). 테이블: equipment_data
 * 한 번 저장하면 바꾸지 않는다. 상태 이력, 정지 기록, 생산 집계는 이 데이터를 바탕으로 따로 만든다.
 */
@Entity
@Table(name = "equipment_data",
        uniqueConstraints = @UniqueConstraint(name = "uq_equipment_data_message", columnNames = "message_id"))   // DDL과 같은 제약 (H2 테스트 테이블에도 생기도록)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // 번호는 DB(IDENTITY 컬럼)가 매긴다
    private Long dataId;

    /** 메시지 고유 번호. 같은 메시지가 두 번 와도 한 번만 저장하는 기준 (UNIQUE 제약). */
    private String messageId;

    private String equipCode;

    /** 설비에서 측정한 시각 */
    private LocalDateTime collectedAt;

    /** 서버가 받은 시각. collectedAt과의 차이로 전송이 얼마나 늦었는지 알 수 있다. */
    private LocalDateTime receivedAt;

    private Integer cycleCount;

    private String status;

    @Column(precision = 8, scale = 3)
    private BigDecimal spindleVibration;

    @Column(precision = 6, scale = 2)
    private BigDecimal spindleTemp;

    public static EquipmentData of(String messageId, String equipCode, LocalDateTime collectedAt, LocalDateTime receivedAt,
                                   Integer cycleCount, String status, BigDecimal spindleVibration, BigDecimal spindleTemp) {
        EquipmentData data = new EquipmentData();
        data.messageId = messageId;
        data.equipCode = equipCode;
        data.collectedAt = collectedAt;
        data.receivedAt = receivedAt;
        data.cycleCount = cycleCount;
        data.status = status;
        data.spindleVibration = spindleVibration;
        data.spindleTemp = spindleTemp;
        return data;
    }
}
