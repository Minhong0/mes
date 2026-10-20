-- LOT 추적. 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- LOT으로 관리하는 품목. 반제품·완제품과 개수로 들어가는 원자재(환봉, 키홈 핀)는 Y,
-- 조금씩 쓰는 소모품(절삭유, 연삭석, 포장 트레이)은 N (투입 LOT을 고르지 않는다).
IF COL_LENGTH('item', 'lot_yn') IS NULL
    ALTER TABLE item ADD lot_yn CHAR(1) NOT NULL
        CONSTRAINT df_item_lot_yn DEFAULT 'N'
        CONSTRAINT ck_item_lot_yn CHECK (lot_yn IN ('Y', 'N'));
GO

-- 이미 기준정보를 넣은 DB용. 빈 DB라면 바뀌는 행이 없고, seed(01_master.sql)가 lot_yn을 함께 넣는다.
UPDATE item SET lot_yn = 'Y'
WHERE lot_yn = 'N' AND (item_type IN ('FG', 'SF') OR item_code IN ('RM-SCM-001', 'RM-KEY-001'));
GO

-- 이력이 어느 실적에서 생겼는지. 실적을 취소하면 이 실적의 투입·산출 이력을 찾아 되돌린다.
IF COL_LENGTH('lot_history', 'result_id') IS NULL
    ALTER TABLE lot_history ADD result_id BIGINT NULL
        CONSTRAINT fk_lot_history_result REFERENCES work_result (result_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_lot_history_result')
    CREATE INDEX ix_lot_history_result ON lot_history (result_id);
GO

-- 이벤트 종류에 CANCEL(실적 취소로 되돌림)을 더한다.
-- 03_lot.sql의 CHECK는 이름 없이 만들어 SQL Server가 붙인 이름을 찾아서 지운다.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_lot_history_event_type')
BEGIN
    DECLARE @name SYSNAME = (
        SELECT cc.name
        FROM sys.check_constraints cc
        JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
        WHERE cc.parent_object_id = OBJECT_ID('lot_history') AND c.name = 'event_type');
    IF @name IS NOT NULL
        EXEC ('ALTER TABLE lot_history DROP CONSTRAINT ' + @name);
    ALTER TABLE lot_history ADD CONSTRAINT ck_lot_history_event_type CHECK (event_type IN
        ('RECEIVE', 'PRODUCE', 'CONSUME', 'MERGE', 'SPLIT', 'INSPECT', 'HOLD', 'RELEASE', 'SHIP', 'SCRAP', 'CANCEL'));
END
GO
