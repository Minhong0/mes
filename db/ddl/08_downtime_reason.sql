-- POP 정지 사유 입력. 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 설비가 보낸 사유는 처음 판단이고, 작업자가 POP에서 사유를 확정하면 그 시각을 남긴다.
-- 누가 확정했는지는 기존 worker_id 컬럼에 남는다. NULL이면 아직 작업자가 확인하지 않은 정지다.
IF COL_LENGTH('downtime', 'reason_at') IS NULL
    ALTER TABLE downtime ADD reason_at DATETIME2(0) NULL;
GO
