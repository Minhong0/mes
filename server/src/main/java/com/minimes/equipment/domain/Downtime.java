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

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 비가동(정지) 기록. 테이블: downtime
 * 부트캠프 과거 실적(BOOTCAMP)과 설비가 보낸 정지(EQUIP)를 같은 테이블에 담아, OEE 계산 쿼리를 하나로 쓴다.
 */
@Entity
@Table(name = "downtime")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Downtime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long downtimeId;

    private String equipCode;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;          // null이면 아직 정지 중

    private Integer durationMin;            // 끝날 때 계산해 저장

    private String dtCat;                   // 공통코드 DT_CAT (대분류)

    private String dtSub;                   // 공통코드 DT_SUB (소분류)

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DowntimeSource source;

    private String workerId;

    private LocalDateTime createdAt;

    /** 설비가 보낸 정지 메시지로 비가동을 시작한다. */
    public static Downtime startFromEquipment(String equipCode, String dtCat, String dtSub,
                                              LocalDateTime startedAt, LocalDateTime now) {
        Downtime downtime = new Downtime();
        downtime.equipCode = equipCode;
        downtime.dtCat = dtCat;
        downtime.dtSub = dtSub;
        downtime.startedAt = startedAt;
        downtime.source = DowntimeSource.EQUIP;
        downtime.createdAt = now;
        return downtime;
    }

    /** 정지가 끝났다. 정지 시간(분)은 반올림한다. 예: 12분 40초 → 13분 */
    public void end(LocalDateTime at) {
        this.endedAt = at;
        this.durationMin = (int) Math.round(Duration.between(startedAt, at).toSeconds() / 60.0);
    }
}
