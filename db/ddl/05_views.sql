-- 조회용 뷰: OEE
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 설비·일자별 OEE (%). 산식은 부트캠프 2차 자료의 OEE 산출 기준과 같다.
CREATE OR ALTER VIEW v_oee_daily AS
SELECT
    prod_date,
    equip_code,
    source,
    CAST(100.0 * run_min / planned_min AS DECIMAL(6,2))                        AS availability,
    CAST(100.0 * prod_qty * ideal_ct_min / NULLIF(run_min, 0) AS DECIMAL(6,2)) AS performance,
    CAST(100.0 * good_qty / NULLIF(prod_qty, 0) AS DECIMAL(6,2))               AS quality,
    CAST(100.0 * (1.0 * run_min / planned_min)
               * (1.0 * prod_qty * ideal_ct_min / NULLIF(run_min, 0))
               * (1.0 * good_qty / NULLIF(prod_qty, 0)) AS DECIMAL(6,2))       AS oee
FROM production_daily;
GO

-- 설비·월별 OEE (%).
-- 일별 OEE를 평균하지 않고 시간과 수량을 먼저 합산한 뒤 계산한다 (2차 자료 STEP 4 방식).
CREATE OR ALTER VIEW v_oee_monthly AS
WITH m AS (
    SELECT
        DATEFROMPARTS(YEAR(prod_date), MONTH(prod_date), 1) AS prod_month,
        equip_code,
        source,
        SUM(planned_min)             AS planned_min,
        SUM(run_min)                 AS run_min,
        SUM(prod_qty)                AS prod_qty,
        SUM(good_qty)                AS good_qty,
        SUM(prod_qty * ideal_ct_min) AS ideal_min
    FROM production_daily
    GROUP BY DATEFROMPARTS(YEAR(prod_date), MONTH(prod_date), 1), equip_code, source
)
SELECT
    prod_month,
    equip_code,
    source,
    CAST(100.0 * run_min / planned_min AS DECIMAL(6,2))                  AS availability,
    CAST(100.0 * ideal_min / NULLIF(run_min, 0) AS DECIMAL(6,2))         AS performance,
    CAST(100.0 * good_qty / NULLIF(prod_qty, 0) AS DECIMAL(6,2))         AS quality,
    CAST(100.0 * (1.0 * run_min / planned_min)
               * (ideal_min / NULLIF(run_min, 0))
               * (1.0 * good_qty / NULLIF(prod_qty, 0)) AS DECIMAL(6,2)) AS oee
FROM m;
GO
