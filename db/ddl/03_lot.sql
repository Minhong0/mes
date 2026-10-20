-- LOT 추적: LOT, LOT 계보(투입 → 산출), LOT 이벤트 이력, 검사 결과
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE TABLE lot (
    lot_no          VARCHAR(30)   NOT NULL PRIMARY KEY,     -- 품번-yyMMdd-001. 예: SF-SFT-010-261018-001
    item_code       VARCHAR(20)   NOT NULL REFERENCES item (item_code),
    qty             DECIMAL(12,3) NOT NULL CHECK (qty >= 0),   -- 현재 남은 수량
    initial_qty     DECIMAL(12,3) NOT NULL CHECK (initial_qty > 0),
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'HOLD', 'CONSUMED', 'SHIPPED', 'SCRAPPED')),
    wo_no           VARCHAR(20)   NULL REFERENCES work_order (wo_no),    -- 원자재 LOT은 NULL
    last_process    VARCHAR(10)   NULL REFERENCES process (process_code),
    supplier_lot_no VARCHAR(30)   NULL,                                  -- 원자재 입고 시 공급사 LOT
    created_at      DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    created_by      VARCHAR(20)   NULL
);
CREATE INDEX ix_lot_item ON lot (item_code, status);
CREATE INDEX ix_lot_wo ON lot (wo_no);

-- 투입 LOT과 산출 LOT의 N:M 연결. 정/역방향 추적은 이 테이블을 재귀 CTE로 따라간다.
-- 열처리 Batch: 여러 투입 LOT -> 하나의 Batch LOT (병합)
-- 포장: 하나의 투입 LOT -> 여러 트레이 LOT (분할)
CREATE TABLE lot_genealogy (
    genealogy_id   BIGINT IDENTITY NOT NULL PRIMARY KEY,
    input_lot_no   VARCHAR(30)     NOT NULL REFERENCES lot (lot_no),
    output_lot_no  VARCHAR(30)     NOT NULL REFERENCES lot (lot_no),
    qty            DECIMAL(12,3)   NOT NULL CHECK (qty > 0),
    process_code   VARCHAR(10)     NOT NULL REFERENCES process (process_code),
    result_id      BIGINT          NULL REFERENCES work_result (result_id),
    linked_at      DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT ck_lot_genealogy_not_self CHECK (input_lot_no <> output_lot_no)
);
CREATE INDEX ix_lot_genealogy_input ON lot_genealogy (input_lot_no);
CREATE INDEX ix_lot_genealogy_output ON lot_genealogy (output_lot_no);

CREATE TABLE lot_history (
    history_id   BIGINT IDENTITY NOT NULL PRIMARY KEY,
    lot_no       VARCHAR(30)     NOT NULL REFERENCES lot (lot_no),
    event_type   VARCHAR(10)     NOT NULL
                 CHECK (event_type IN ('RECEIVE', 'PRODUCE', 'CONSUME', 'MERGE', 'SPLIT', 'INSPECT', 'HOLD', 'RELEASE', 'SHIP', 'SCRAP')),
    process_code VARCHAR(10)     NULL REFERENCES process (process_code),
    qty          DECIMAL(12,3)   NULL,
    equip_code   VARCHAR(10)     NULL REFERENCES equipment (equip_code),
    worker_id    VARCHAR(20)     NULL REFERENCES worker (worker_id),
    note         NVARCHAR(200)   NULL,
    event_at     DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX ix_lot_history_lot ON lot_history (lot_no, event_at);

CREATE TABLE inspection_result (
    insp_id        BIGINT IDENTITY NOT NULL PRIMARY KEY,
    lot_no         VARCHAR(30)     NOT NULL REFERENCES lot (lot_no),
    process_code   VARCHAR(10)     NOT NULL,
    inspect_code   VARCHAR(10)     NOT NULL,
    sample_no      INT             NOT NULL DEFAULT 1,
    measured_value DECIMAL(12,4)   NOT NULL,
    judgement      CHAR(2)         NOT NULL CHECK (judgement IN ('OK', 'NG')),   -- 서버가 LSL/USL로 판정
    equip_code     VARCHAR(10)     NULL REFERENCES equipment (equip_code),
    worker_id      VARCHAR(20)     NULL REFERENCES worker (worker_id),
    measured_at    DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_inspection_result_spec FOREIGN KEY (process_code, inspect_code)
        REFERENCES inspection_spec (process_code, inspect_code)
);
CREATE INDEX ix_inspection_result_lot ON inspection_result (lot_no);
GO
