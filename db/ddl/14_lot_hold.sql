-- 품질 보류 건. 보류 한 번(원인 LOT, 사유)과 그 보류가 막은 LOT들을 따로 남긴다.
-- LOT은 열린(해제하지 않은) 보류 건이 하나라도 있으면 보류 상태다. 그래서 원인 LOT 하나를 해제해도
-- 다른 원인 LOT 때문에 함께 보류된 하위 LOT은 계속 보류된다.
-- 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID('lot_hold') IS NULL
BEGIN
    CREATE TABLE lot_hold (
        hold_id       BIGINT IDENTITY NOT NULL PRIMARY KEY,
        origin_lot_no VARCHAR(30)     NOT NULL REFERENCES lot (lot_no),   -- 보류를 건 LOT (원인 LOT)
        reason        NVARCHAR(150)   NOT NULL,
        by_inspection BIT             NOT NULL DEFAULT 0,                 -- 검사 불합격 자동 보류
        held_by       VARCHAR(20)     NULL REFERENCES worker (worker_id),
        held_at       DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME()
    );
    CREATE INDEX ix_lot_hold_origin ON lot_hold (origin_lot_no);
END
GO

-- 보류 건이 막은 LOT. 해제는 LOT마다 따로 한다 (이 LOT만 해제할 수 있으므로)
IF OBJECT_ID('lot_hold_target') IS NULL
BEGIN
    CREATE TABLE lot_hold_target (
        target_id      BIGINT IDENTITY NOT NULL PRIMARY KEY,
        hold_id        BIGINT          NOT NULL REFERENCES lot_hold (hold_id),
        lot_no         VARCHAR(30)     NOT NULL REFERENCES lot (lot_no),
        released_by    VARCHAR(20)     NULL REFERENCES worker (worker_id),
        released_at    DATETIME2(0)    NULL,                              -- NULL이면 아직 이 보류 건에 묶여 있다
        release_reason NVARCHAR(150)   NULL,
        CONSTRAINT uq_lot_hold_target UNIQUE (hold_id, lot_no)
    );
    CREATE INDEX ix_lot_hold_target_lot ON lot_hold_target (lot_no, released_at);
END
GO

-- 이 테이블 전에 보류한 LOT을 보류 건으로 옮긴다 (한 번만).
-- 마지막 HOLD 이력의 메모 "사유 (원인 LOT X)"에서 원인 LOT을 꺼내고, 같은 때·같은 원인·같은 사유면 한 건으로 묶는다.
IF NOT EXISTS (SELECT 1 FROM lot_hold) AND EXISTS (SELECT 1 FROM lot WHERE status = 'HOLD')
BEGIN
    SELECT l.lot_no,
           CASE WHEN o.lot_no IS NOT NULL THEN o.lot_no ELSE l.lot_no END AS origin_lot_no,
           LEFT(COALESCE(CASE WHEN p.pos > 0 THEN LEFT(h.note, p.pos - 1) ELSE h.note END, N'(보류 이력 없음)'), 150) AS reason,
           h.worker_id,
           COALESCE(h.event_at, SYSDATETIME()) AS held_at
    INTO #legacy_hold
    FROM lot l
    OUTER APPLY (SELECT TOP 1 lh.note, lh.worker_id, lh.event_at FROM lot_history lh
                 WHERE lh.lot_no = l.lot_no AND lh.event_type = 'HOLD' ORDER BY lh.history_id DESC) h
    OUTER APPLY (SELECT CASE WHEN h.note LIKE N'% (원인 LOT %)' THEN CHARINDEX(N' (원인 LOT ', h.note) ELSE 0 END AS pos) p
    LEFT JOIN lot o ON p.pos > 0 AND o.lot_no = SUBSTRING(h.note, p.pos + 9, LEN(h.note) - p.pos - 9)
    WHERE l.status = 'HOLD';

    INSERT INTO lot_hold (origin_lot_no, reason, by_inspection, held_by, held_at)
    SELECT DISTINCT origin_lot_no, reason, CASE WHEN reason LIKE N'검사 불합격%' THEN 1 ELSE 0 END, worker_id, held_at
    FROM #legacy_hold;

    INSERT INTO lot_hold_target (hold_id, lot_no)
    SELECT h.hold_id, g.lot_no
    FROM #legacy_hold g
    JOIN lot_hold h ON h.origin_lot_no = g.origin_lot_no AND h.reason = g.reason AND h.held_at = g.held_at
                   AND (h.held_by = g.worker_id OR (h.held_by IS NULL AND g.worker_id IS NULL));

    DROP TABLE #legacy_hold;
END
GO
