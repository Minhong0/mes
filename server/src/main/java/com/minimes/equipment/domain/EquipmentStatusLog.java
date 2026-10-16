package com.minimes.equipment.domain;

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

import java.time.LocalDateTime;

/**
 * 설비 상태 이력. 테이블: equipment_status_log
 * 한 행이 "이 상태가 언제부터 언제까지 이어졌나"를 나타낸다. ended_at이 null이면 지금 그 상태다.
 *
 *   RUN  09:00 ~ 10:12
 *   DOWN 10:12 ~ 10:45   ← 상태가 바뀌면 앞 행을 닫고 새 행을 연다
 *   RUN  10:45 ~ (null)
 */
@Entity
@Table(name = "equipment_status_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    private String equipCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private EquipmentStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    public static EquipmentStatusLog start(String equipCode, EquipmentStatus status, LocalDateTime at) {
        EquipmentStatusLog log = new EquipmentStatusLog();
        log.equipCode = equipCode;
        log.status = status;
        log.startedAt = at;
        return log;
    }

    public void end(LocalDateTime at) {
        this.endedAt = at;
    }
}
