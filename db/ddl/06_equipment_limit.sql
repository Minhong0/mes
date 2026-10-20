-- 설비별 센서 관리한계 (CBM 알람 기준). 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 만든다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 관리한계는 기준 기간의 가동(RUN) 데이터로 계산한다: UCL = 평균 + 3 × 표준편차.
-- 계산 결과와 근거(기간, 표본 수)를 함께 남겨, 언제 어떤 데이터로 정한 한계인지 알 수 있게 한다.
IF OBJECT_ID('equipment_limit', 'U') IS NULL
CREATE TABLE equipment_limit (
    equip_code    VARCHAR(10)     NOT NULL REFERENCES equipment (equip_code),
    metric        VARCHAR(20)     NOT NULL CHECK (metric IN ('VIBRATION', 'TEMP')),
    center_value  DECIMAL(12,3)   NOT NULL,          -- 평균 (중심선 CL)
    sigma         DECIMAL(12,4)   NOT NULL,          -- 표준편차
    ucl           DECIMAL(12,3)   NOT NULL,          -- 관리 상한 = 평균 + 3σ
    sample_count  INT             NOT NULL,
    base_from     DATETIME2(0)    NOT NULL,          -- 기준 기간 (측정 시각)
    base_to       DATETIME2(0)    NOT NULL,
    calculated_at DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_equipment_limit PRIMARY KEY (equip_code, metric)
);
GO
