-- 포장 분할. 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 이 공정에서 만드는 LOT 하나의 최대 수량. 양품이 더 많으면 이 수량씩 여러 LOT으로 나눈다.
-- NULL이면 나누지 않는다 (실적 하나 = LOT 하나). 조립·포장(OP-70)은 20구 트레이라 20.
IF COL_LENGTH('routing', 'lot_size') IS NULL
    ALTER TABLE routing ADD lot_size INT NULL CONSTRAINT ck_routing_lot_size CHECK (lot_size > 0);
GO

-- 이미 기준정보를 넣은 DB용. 빈 DB라면 seed(01_master.sql)가 lot_size를 함께 넣는다.
UPDATE routing SET lot_size = 20
WHERE item_code = 'FG-SFT-100' AND process_code = 'OP-70' AND lot_size IS NULL;
GO
