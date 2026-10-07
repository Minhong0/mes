-- 기준정보: 공통코드, 품목, BOM, 공정, 작업장, 라우팅, 설비, 검사 규격, 작업자, 공구
-- 초기 데이터 출처: data/master/*.csv
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE TABLE common_code_group (
    group_code  VARCHAR(30)   NOT NULL PRIMARY KEY,
    group_name  NVARCHAR(50)  NOT NULL,
    created_at  DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE common_code (
    group_code  VARCHAR(30)   NOT NULL REFERENCES common_code_group (group_code),
    code        VARCHAR(30)   NOT NULL,
    code_name   NVARCHAR(50)  NOT NULL,
    parent_code VARCHAR(30)   NULL,          -- 예: 비가동 소분류(DT_SUB) → 대분류(DT_CAT) 코드
    sort_order  INT           NOT NULL DEFAULT 0,
    use_yn      CHAR(1)       NOT NULL DEFAULT 'Y' CHECK (use_yn IN ('Y', 'N')),
    created_at  DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_common_code PRIMARY KEY (group_code, code)
);

CREATE TABLE item (
    item_code   VARCHAR(20)   NOT NULL PRIMARY KEY,
    item_name   NVARCHAR(100) NOT NULL,
    item_type   VARCHAR(10)   NOT NULL CHECK (item_type IN ('FG', 'SF', 'RM')),
    spec        NVARCHAR(50)  NULL,
    unit        VARCHAR(10)   NOT NULL,
    note        NVARCHAR(200) NULL,
    use_yn      CHAR(1)       NOT NULL DEFAULT 'Y' CHECK (use_yn IN ('Y', 'N')),
    created_at  DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    created_by  VARCHAR(20)   NULL,
    updated_at  DATETIME2(0)  NULL,
    updated_by  VARCHAR(20)   NULL
);

CREATE TABLE process (
    process_code    VARCHAR(10)   NOT NULL PRIMARY KEY,
    process_name    NVARCHAR(50)  NOT NULL,
    process_name_en VARCHAR(50)   NULL,
    department      NVARCHAR(30)  NULL,
    created_at      DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE bom (
    bom_id           INT IDENTITY  NOT NULL PRIMARY KEY,
    parent_item_code VARCHAR(20)   NOT NULL REFERENCES item (item_code),
    child_item_code  VARCHAR(20)   NOT NULL REFERENCES item (item_code),
    qty              DECIMAL(12,4) NOT NULL CHECK (qty > 0),
    unit             VARCHAR(10)   NOT NULL,
    process_code     VARCHAR(10)   NOT NULL REFERENCES process (process_code),   -- 하위 품목이 투입되는 공정
    created_at       DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT uq_bom UNIQUE (parent_item_code, child_item_code),
    CONSTRAINT ck_bom_not_self CHECK (parent_item_code <> child_item_code)
);
-- BOM Level은 저장하지 않고 재귀 CTE로 계산한다.

CREATE TABLE work_center (
    wc_code      VARCHAR(10)   NOT NULL PRIMARY KEY,
    wc_name      NVARCHAR(50)  NOT NULL,
    process_code VARCHAR(10)   NOT NULL REFERENCES process (process_code),
    location     NVARCHAR(30)  NULL,
    workers      INT           NULL,
    note         NVARCHAR(200) NULL,
    created_at   DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE routing (
    item_code        VARCHAR(20)   NOT NULL REFERENCES item (item_code),
    seq              INT           NOT NULL,
    process_code     VARCHAR(10)   NOT NULL REFERENCES process (process_code),
    wc_code          VARCHAR(10)   NOT NULL REFERENCES work_center (wc_code),
    output_item_code VARCHAR(20)   NOT NULL REFERENCES item (item_code),
    std_time_min     DECIMAL(10,2) NOT NULL,          -- time_basis가 EA면 개당, BATCH면 Batch당 시간
    time_basis       VARCHAR(10)   NOT NULL CHECK (time_basis IN ('EA', 'BATCH')),
    batch_max_qty    INT           NULL,
    setup_time_min   DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at       DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_routing PRIMARY KEY (item_code, seq),
    CONSTRAINT ck_routing_batch CHECK (time_basis = 'EA' OR batch_max_qty > 0)
);

CREATE TABLE equipment (
    equip_code          VARCHAR(10)   NOT NULL PRIMARY KEY,
    equip_name          NVARCHAR(50)  NOT NULL,
    wc_code             VARCHAR(10)   NOT NULL REFERENCES work_center (wc_code),
    equip_type          VARCHAR(20)   NOT NULL,
    maker               VARCHAR(50)   NULL,
    model               VARCHAR(50)   NULL,
    install_year        SMALLINT      NULL,
    ideal_ct_min        DECIMAL(10,2) NULL,           -- 이론 사이클타임(분/개), OEE 성능효율 계산용
    planned_min_per_day INT           NULL,           -- 계획 가동시간(분/일)
    auto_collect        CHAR(1)       NOT NULL DEFAULT 'N' CHECK (auto_collect IN ('Y', 'N')),
    status              VARCHAR(10)   NOT NULL DEFAULT 'IDLE',   -- 공통코드 EQUIP_STATUS
    note                NVARCHAR(200) NULL,
    created_at          DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE inspection_spec (
    process_code VARCHAR(10)   NOT NULL REFERENCES process (process_code),
    inspect_code VARCHAR(10)   NOT NULL,
    inspect_name NVARCHAR(50)  NOT NULL,
    equip_code   VARCHAR(10)   NULL REFERENCES equipment (equip_code),
    unit         VARCHAR(10)   NOT NULL,
    nominal      DECIMAL(12,4) NULL,
    lsl          DECIMAL(12,4) NULL,                  -- 하한, NULL이면 하한 없음
    usl          DECIMAL(12,4) NULL,                  -- 상한, NULL이면 상한 없음
    created_at   DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_inspection_spec PRIMARY KEY (process_code, inspect_code),
    CONSTRAINT ck_inspection_spec_limit CHECK (lsl IS NOT NULL OR usl IS NOT NULL),
    CONSTRAINT ck_inspection_spec_range CHECK (lsl IS NULL OR usl IS NULL OR lsl < usl)
);

CREATE TABLE worker (
    worker_id   VARCHAR(20)   NOT NULL PRIMARY KEY,
    worker_name NVARCHAR(30)  NOT NULL,
    wc_code     VARCHAR(10)   NULL REFERENCES work_center (wc_code),
    role        VARCHAR(10)   NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'QC')),
    use_yn      CHAR(1)       NOT NULL DEFAULT 'Y' CHECK (use_yn IN ('Y', 'N')),
    created_at  DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME()
);

-- 공구 수명 관리 (인서트, 엔드밀, 드릴, 연삭석)
CREATE TABLE tool (
    tool_code     VARCHAR(20)   NOT NULL PRIMARY KEY,
    tool_name     NVARCHAR(50)  NOT NULL,
    tool_type     VARCHAR(10)   NOT NULL CHECK (tool_type IN ('INSERT', 'ENDMILL', 'DRILL', 'WHEEL')),
    equip_code    VARCHAR(10)   NOT NULL REFERENCES equipment (equip_code),
    life_count    INT           NOT NULL CHECK (life_count > 0),   -- 교체 기준 가공 수량
    used_count    INT           NOT NULL DEFAULT 0,                -- 현재 공구로 가공한 수량
    installed_at  DATETIME2(0)  NULL,
    created_at    DATETIME2(0)  NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE tool_change_history (
    change_id      BIGINT IDENTITY NOT NULL PRIMARY KEY,
    tool_code      VARCHAR(20)     NOT NULL REFERENCES tool (tool_code),
    changed_at     DATETIME2(0)    NOT NULL DEFAULT SYSDATETIME(),
    used_count     INT             NOT NULL,     -- 교체 시점까지 가공한 수량
    reason         NVARCHAR(100)   NULL,
    worker_id      VARCHAR(20)     NULL REFERENCES worker (worker_id)
);
GO
