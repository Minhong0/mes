-- 설비: 수집 데이터, 상태 이력, 비가동, 일별 생산 집계, 알람
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 시뮬레이터(MQTT)가 보낸 원천 데이터. 빠르게 쌓이므로 설비 + 시각 인덱스를 둔다.
CREATE TABLE equipment_data (
    data_id           BIGINT IDENTITY NOT NULL PRIMARY KEY,
    message_id        VARCHAR(50)     NOT NULL,      -- 재전송 시 중복 저장 방지
    equip_code        VARCHAR(10)     NOT NULL REFERENCES equipment (equip_code),
    collected_at      DATETIME2(3)    NOT NULL,      -- 설비에서 측정한 시각
    received_at       DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    cycle_count       INT             NULL,          -- 가공 완료 누적 카운트
    status            VARCHAR(10)     NULL,          -- 공통코드 EQUIP_STATUS
    spindle_vibration DECIMAL(8,3)    NULL,          -- mm/s (가상 데이터)
    spindle_temp      DECIMAL(6,2)    NULL,          -- °C (가상 데이터)
    CONSTRAINT uq_equipment_data_message UNIQUE (message_id)
);
CREATE INDEX ix_equipment_data_equip_time ON equipment_data (equip_code, collected_at);

CREATE TABLE equipment_status_log (
    log_id      BIGINT IDENTITY NOT NULL PRIMARY KEY,
    equip_code  VARCHAR(10)     NOT NULL REFERENCES equipment (equip_code),
    status      VARCHAR(10)     NOT NULL,
    started_at  DATETIME2(0)    NOT NULL,
    ended_at    DATETIME2(0)    NULL                 -- NULL이면 현재 상태
);
CREATE INDEX ix_equipment_status_log ON equipment_status_log (equip_code, started_at);

CREATE TABLE downtime (
    downtime_id  BIGINT IDENTITY NOT NULL PRIMARY KEY,
    equip_code   VARCHAR(10)     NOT NULL REFERENCES equipment (equip_code),
    started_at   DATETIME2(0)    NOT NULL,
    ended_at     DATETIME2(0)    NULL,
    duration_min INT             NULL,               -- 종료 시 계산해 저장
    dt_cat       VARCHAR(30)     NOT NULL,           -- 공통코드 DT_CAT
    dt_sub       VARCHAR(30)     NULL,               -- 공통코드 DT_SUB
    note         NVARCHAR(200)   NULL,
    source       VARCHAR(10)     NOT NULL CHECK (source IN ('BOOTCAMP', 'POP', 'EQUIP')),
    worker_id    VARCHAR(20)     NULL REFERENCES worker (worker_id),
    created_at   DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT ck_downtime_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX ix_downtime_equip_time ON downtime (equip_code, started_at);

-- 설비·일자별 생산 집계.
-- 부트캠프 과거 실적(BOOTCAMP)과 MES 집계(MES)를 같은 구조로 저장해 OEE를 같은 쿼리로 계산한다.
CREATE TABLE production_daily (
    prod_date    DATE            NOT NULL,
    equip_code   VARCHAR(10)     NOT NULL REFERENCES equipment (equip_code),
    planned_min  INT             NOT NULL CHECK (planned_min > 0),
    downtime_min INT             NOT NULL DEFAULT 0,
    run_min      AS (planned_min - downtime_min) PERSISTED,
    prod_qty     INT             NOT NULL DEFAULT 0,
    good_qty     INT             NOT NULL DEFAULT 0,
    defect_qty   INT             NOT NULL DEFAULT 0,
    ideal_ct_min DECIMAL(10,2)   NOT NULL,           -- 집계 당시의 이론 사이클타임
    source       VARCHAR(10)     NOT NULL CHECK (source IN ('BOOTCAMP', 'MES')),
    created_at   DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_production_daily PRIMARY KEY (prod_date, equip_code),
    CONSTRAINT ck_production_daily_qty CHECK (prod_qty = good_qty + defect_qty),
    CONSTRAINT ck_production_daily_time CHECK (downtime_min BETWEEN 0 AND planned_min)
);

-- CBM(진동·온도 관리한계 초과)과 공구 수명 알람
CREATE TABLE equipment_alarm (
    alarm_id       BIGINT IDENTITY NOT NULL PRIMARY KEY,
    equip_code     VARCHAR(10)     NOT NULL REFERENCES equipment (equip_code),
    alarm_type     VARCHAR(20)     NOT NULL CHECK (alarm_type IN ('VIBRATION_UCL', 'TEMP_UCL', 'TOOL_LIFE')),
    measured_value DECIMAL(12,3)   NULL,
    limit_value    DECIMAL(12,3)   NULL,
    tool_code      VARCHAR(20)     NULL REFERENCES tool (tool_code),
    occurred_at    DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    ack_at         DATETIME2(0)    NULL,             -- 담당자 확인 시각
    ack_by         VARCHAR(20)     NULL
);
CREATE INDEX ix_equipment_alarm_open ON equipment_alarm (equip_code, ack_at);
GO
