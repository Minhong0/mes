-- POP 실적 중복 등록 방지. 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- POP가 실적을 보낼 때 요청마다 고유 ID(GUID)를 붙인다. 응답을 못 받아 다시 보내도 같은 ID라 한 번만 저장된다.
-- 설비 메시지의 message_id와 같은 방식이다.
IF COL_LENGTH('work_result', 'request_id') IS NULL
    ALTER TABLE work_result ADD request_id VARCHAR(50) NULL;
GO

-- 필터 인덱스: request_id가 있는 행끼리만 중복을 막는다 (예전 실적이나 설비 실적은 NULL이어도 된다).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_work_result_request')
    CREATE UNIQUE INDEX uq_work_result_request ON work_result (request_id) WHERE request_id IS NOT NULL;
GO
