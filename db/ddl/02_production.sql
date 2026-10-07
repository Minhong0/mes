-- 생산: 작업지시, 공정별 작업, 생산실적, 불량
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE TABLE work_order (
    wo_no          VARCHAR(20)   NOT NULL PRIMARY KEY,     -- 예: WO-251001-001
    item_code      VARCHAR(20)   NOT NULL REFERENCES item (item_code),
    plan_qty       INT           NOT NULL CHECK (plan_qty > 0),
    due_date       DATE          NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'WAIT'
                   CHECK (status IN ('WAIT', 'RUN', 'DONE', 'CLOSED')),   -- 공통코드 WO_STATUS
    actual_start   DATETIME2(0)  NULL,
    actual_end     DATETIME2(0)  NULL,
    note           NVARCHAR(200) NULL,
    created_at     DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    created_by     VARCHAR(20)   NULL,
    updated_at     DATETIME2(0)  NULL,
    updated_by     VARCHAR(20)   NULL
);

-- 작업지시 생성 시 라우팅을 따라 공정별로 한 행씩 만든다.
CREATE TABLE work_order_operation (
    wo_op_id     BIGINT IDENTITY NOT NULL PRIMARY KEY,
    wo_no        VARCHAR(20)     NOT NULL REFERENCES work_order (wo_no),
    seq          INT             NOT NULL,
    process_code VARCHAR(10)     NOT NULL REFERENCES process (process_code),
    wc_code      VARCHAR(10)     NOT NULL REFERENCES work_center (wc_code),
    status       VARCHAR(10)     NOT NULL DEFAULT 'WAIT'
                 CHECK (status IN ('WAIT', 'RUN', 'DONE')),
    good_qty     INT             NOT NULL DEFAULT 0,      -- 실적 합계 (취소분 제외)
    defect_qty   INT             NOT NULL DEFAULT 0,
    started_at   DATETIME2(0)    NULL,
    ended_at     DATETIME2(0)    NULL,
    CONSTRAINT uq_work_order_operation UNIQUE (wo_no, seq)
);

-- 실적은 수정하지 않고, 잘못 입력하면 취소 표시 후 새로 등록한다.
CREATE TABLE work_result (
    result_id     BIGINT IDENTITY NOT NULL PRIMARY KEY,
    wo_op_id      BIGINT          NOT NULL REFERENCES work_order_operation (wo_op_id),
    equip_code    VARCHAR(10)     NULL REFERENCES equipment (equip_code),
    worker_id     VARCHAR(20)     NULL REFERENCES worker (worker_id),
    started_at    DATETIME2(0)    NOT NULL,
    ended_at      DATETIME2(0)    NULL,
    good_qty      INT             NOT NULL DEFAULT 0 CHECK (good_qty >= 0),
    defect_qty    INT             NOT NULL DEFAULT 0 CHECK (defect_qty >= 0),
    source        VARCHAR(10)     NOT NULL CHECK (source IN ('POP', 'EQUIP')),   -- 작업자 입력 / 설비 자동 수집
    cancelled     BIT             NOT NULL DEFAULT 0,
    cancelled_at  DATETIME2(0)    NULL,
    cancelled_by  VARCHAR(20)     NULL,
    cancel_reason NVARCHAR(200)   NULL,
    created_at    DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    created_by    VARCHAR(20)     NULL,
    CONSTRAINT ck_work_result_cancel CHECK (cancelled = 0 OR cancelled_at IS NOT NULL)
);
CREATE INDEX ix_work_result_op ON work_result (wo_op_id);
CREATE INDEX ix_work_result_equip_time ON work_result (equip_code, started_at);

CREATE TABLE defect (
    defect_id   BIGINT IDENTITY NOT NULL PRIMARY KEY,
    result_id   BIGINT          NOT NULL REFERENCES work_result (result_id),
    defect_type VARCHAR(20)     NOT NULL,      -- 공통코드 DEFECT_TYPE
    qty         INT             NOT NULL CHECK (qty > 0),
    note        NVARCHAR(200)   NULL,
    created_at  DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX ix_defect_result ON defect (result_id);
GO
