-- 수집 데이터 초기화: 시뮬레이터와 수집기가 만든 데이터만 지운다.
-- 기준정보와 부트캠프 과거 실적(source = 'BOOTCAMP')은 그대로 둔다.
-- 서버를 끄고 실행한다 (실행 중이면 집계 스케줄러가 지운 직후 다시 만들 수 있다).
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

DELETE FROM production_daily WHERE source = 'MES';
DELETE FROM downtime WHERE source = 'EQUIP';
DELETE FROM equipment_status_log;
DELETE FROM equipment_data;
UPDATE equipment SET status = 'IDLE' WHERE auto_collect = 'Y';
GO
