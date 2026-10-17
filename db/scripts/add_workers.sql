-- 이미 만든 DB에 POP 작업자(가상 인물)를 넣는다. 이미 있는 사번은 건너뛰므로 여러 번 실행해도 된다.
-- 새 DB는 seed/01_master.sql에 같은 데이터가 들어 있다 (출처: data/master/workers.csv).
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

INSERT INTO worker (worker_id, worker_name, wc_code, role)
SELECT v.worker_id, v.worker_name, v.wc_code, v.role
FROM (VALUES
    ('W1001', N'김도윤', 'WC-100', 'OPERATOR'),
    ('W1002', N'이서준', 'WC-100', 'OPERATOR'),
    ('W2001', N'박지호', 'WC-200', 'OPERATOR'),
    ('W2002', N'최하준', 'WC-200', 'OPERATOR'),
    ('W4001', N'정민재', 'WC-400', 'OPERATOR'),
    ('W6001', N'강예린', 'WC-600', 'QC'),
    ('W9001', N'윤서연', NULL, 'ADMIN')
) AS v (worker_id, worker_name, wc_code, role)
WHERE NOT EXISTS (SELECT 1 FROM worker w WHERE w.worker_id = v.worker_id);
GO
