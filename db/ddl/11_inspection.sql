-- 검사 결과. 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 같은 LOT, 같은 공정의 같은 항목·시료는 한 번만. 같은 검사를 두 번 보내도 두 줄이 생기지 않는다.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_inspection_result_sample')
    CREATE UNIQUE INDEX ux_inspection_result_sample
        ON inspection_result (lot_no, process_code, inspect_code, sample_no);
GO
