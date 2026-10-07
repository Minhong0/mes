# ERD

DDL은 [`db/ddl/`](../db/ddl)에 있습니다. 테이블 25개, 뷰 2개이며 다섯 영역으로 나뉩니다.
아래 그림은 키와 관계 위주로 줄인 것이고, 전체 컬럼은 DDL을 참고하세요.

## 1. 기준정보

```mermaid
erDiagram
    ITEM ||--o{ BOM : "상위 품목"
    ITEM ||--o{ BOM : "하위 품목"
    ITEM ||--o{ ROUTING : "공정 순서"
    PROCESS ||--o{ ROUTING : ""
    WORK_CENTER ||--o{ ROUTING : ""
    PROCESS ||--o{ WORK_CENTER : ""
    WORK_CENTER ||--o{ EQUIPMENT : "보유"
    PROCESS ||--o{ INSPECTION_SPEC : "검사 항목"
    EQUIPMENT ||--o{ TOOL : "장착"
    TOOL ||--o{ TOOL_CHANGE_HISTORY : "교체 이력"
    COMMON_CODE_GROUP ||--o{ COMMON_CODE : ""

    ITEM {
        varchar item_code PK
        nvarchar item_name
        varchar item_type "FG, SF, RM"
    }
    BOM {
        int bom_id PK
        varchar parent_item_code FK
        varchar child_item_code FK
        decimal qty
        varchar process_code FK
    }
    ROUTING {
        varchar item_code PK
        int seq PK
        varchar process_code FK
        varchar wc_code FK
        varchar output_item_code FK
        decimal std_time_min
        varchar time_basis "EA, BATCH"
    }
    EQUIPMENT {
        varchar equip_code PK
        varchar wc_code FK
        decimal ideal_ct_min
        char auto_collect
    }
    INSPECTION_SPEC {
        varchar process_code PK
        varchar inspect_code PK
        decimal lsl
        decimal usl
    }
```

## 2. 생산과 LOT 추적

```mermaid
erDiagram
    ITEM ||--o{ WORK_ORDER : ""
    WORK_ORDER ||--|{ WORK_ORDER_OPERATION : "라우팅대로 생성"
    WORK_ORDER_OPERATION ||--o{ WORK_RESULT : "실적"
    WORK_RESULT ||--o{ DEFECT : "불량"
    WORK_ORDER ||--o{ LOT : "생산 LOT"
    LOT ||--o{ LOT_GENEALOGY : "투입"
    LOT ||--o{ LOT_GENEALOGY : "산출"
    WORK_RESULT ||--o{ LOT_GENEALOGY : ""
    LOT ||--o{ LOT_HISTORY : "이벤트"
    LOT ||--o{ INSPECTION_RESULT : "검사"
    INSPECTION_SPEC ||--o{ INSPECTION_RESULT : "규격"

    WORK_ORDER {
        varchar wo_no PK
        varchar item_code FK
        int plan_qty
        varchar status "WAIT, RUN, DONE, CLOSED"
    }
    WORK_ORDER_OPERATION {
        bigint wo_op_id PK
        varchar wo_no FK
        int seq
        varchar process_code FK
    }
    WORK_RESULT {
        bigint result_id PK
        bigint wo_op_id FK
        varchar equip_code FK
        int good_qty
        int defect_qty
        varchar source "POP, EQUIP"
        bit cancelled
    }
    LOT {
        varchar lot_no PK
        varchar item_code FK
        decimal qty
        varchar status
    }
    LOT_GENEALOGY {
        bigint genealogy_id PK
        varchar input_lot_no FK
        varchar output_lot_no FK
        decimal qty
    }
```

## 3. 설비

```mermaid
erDiagram
    EQUIPMENT ||--o{ EQUIPMENT_DATA : "MQTT 수집"
    EQUIPMENT ||--o{ EQUIPMENT_STATUS_LOG : "상태 변화"
    EQUIPMENT ||--o{ DOWNTIME : "비가동"
    EQUIPMENT ||--o{ PRODUCTION_DAILY : "일별 집계"
    EQUIPMENT ||--o{ EQUIPMENT_ALARM : "알람"

    EQUIPMENT_DATA {
        bigint data_id PK
        varchar message_id UK "중복 수신 방지"
        varchar equip_code FK
        datetime2 collected_at
        int cycle_count
        decimal spindle_vibration
    }
    DOWNTIME {
        bigint downtime_id PK
        varchar equip_code FK
        datetime2 started_at
        int duration_min
        varchar dt_cat
        varchar dt_sub
        varchar source "BOOTCAMP, POP, EQUIP"
    }
    PRODUCTION_DAILY {
        date prod_date PK
        varchar equip_code PK
        int planned_min
        int downtime_min
        int run_min "계산 컬럼"
        int prod_qty
        int good_qty
        varchar source "BOOTCAMP, MES"
    }
```

## 설계 포인트

| 결정 | 이유 |
| --- | --- |
| LOT 관계를 `lot_genealogy`(투입 → 산출) 한 테이블로 표현 | 열처리 Batch 병합(N → 1)과 포장 분할(1 → N)을 같은 구조로 다루고, 재귀 CTE 하나로 정/역방향 추적 |
| 실적은 수정 없이 `cancelled` 표시 | 누가 언제 실적을 바꿨는지 감사 추적 가능 |
| `equipment_data.message_id` 유니크 | 시뮬레이터가 통신 장애 뒤 재전송해도 중복 저장되지 않음 |
| `production_daily`에 부트캠프 실적과 MES 집계를 함께 저장 | OEE를 같은 뷰(`v_oee_daily`, `v_oee_monthly`)로 계산해 엑셀 결과와 비교 |
| 월별 OEE는 일별 OEE의 평균이 아니라 합산 후 계산 | 부트캠프 2차 자료의 STEP 4 방식과 맞춤 |
| BOM Level 미저장 | 원본의 Level 번호 오류를 반복하지 않고 재귀 CTE로 계산 |
| 3단계 기능(자재 재고)은 아직 테이블 없음 | 1~2단계 완성 후 추가 |
