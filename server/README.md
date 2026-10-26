# MES 서버 (Spring Boot)

Java 21, Spring Boot 4.0, JPA + MyBatis, MSSQL.

## IntelliJ에서 열기

1. IntelliJ에서 **File → Open**을 누르고 `MES/server` 폴더(또는 `server/build.gradle`)를 선택합니다. **Open as Project**로 엽니다.
2. 오른쪽 아래에 Gradle 프로젝트를 불러오는 진행 표시가 끝날 때까지 기다립니다.
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle**에서 Gradle JVM이 Java 21인지 확인합니다.
4. Lombok 플러그인은 IntelliJ에 기본으로 들어 있습니다. 처음 열 때 "Enable annotation processing" 알림이 뜨면 허용합니다.

## 실행

1. 저장소 루트에서 `docker compose up -d`로 MSSQL과 MQTT 브로커를 띄웁니다. DB와 초기 데이터는 [db/README.md](../db/README.md)를 참고하세요.
2. `MesServerApplication`의 ▶ 버튼으로 실행합니다. DB 비밀번호는 저장소 루트의 `.env`에서 자동으로 읽습니다.
3. http://localhost:8080/swagger-ui.html 에서 API를 확인하고 직접 호출해 볼 수 있습니다.
4. 설비 시뮬레이터(`simulator/MiniMes.Simulator`에서 `dotnet run`)를 실행하면, 서버의 MQTT 수집기가 메시지를 받아 `equipment_data`에 저장합니다.

## 관리자 화면

모든 화면 맨 위에 같은 메뉴(설비 현황, 정지 사유 파레토, 작업지시, LOT 추적, 보류 LOT)가 있습니다. 메뉴는 `templates/fragments/nav.html` 하나이고, 각 화면이 `th:replace`로 넣습니다. http://localhost:8080/ 은 화면 목록입니다.

### 작업지시

http://localhost:8080/work-orders

- **새 작업지시**: 품목(완제품), 계획 수량, 납기, 비고를 넣고 [만들기]. 라우팅의 공정이 함께 생깁니다 (`POST /api/work-orders`).
- **목록**: 대기·진행·완료·마감으로 거를 수 있고, 주소(`?status=RUN`)에도 남습니다.
  - 공정마다 칩 하나에 공정 이름과 양품 수량이 나옵니다 (완료는 옅게, 진행은 초록, 대기는 점선).
  - 납기가 지났는데 대기·진행이면 "지연"으로 표시합니다.
- **버튼**: 대기면 [시작], 완료면 [마감]. 완료는 모든 공정이 끝나면 서버가 바꾸므로 버튼이 없습니다.
- 목록은 한 번에 모두 읽고 화면에서 거릅니다. 위의 숫자(대기, 진행, 완료, 납기 지남)는 늘 전체 기준입니다.

### 보류 LOT

http://localhost:8080/lots/hold

- 지금 HOLD인 LOT과 마지막 보류(때, 누가, 사유, 원인 LOT)를 최근 것부터 보여 줍니다 (`GET /api/lots/held`).
- 원인은 "검사 불합격"(검사 NG로 자동 보류)과 "직접 보류"(추적 화면에서 품질 담당이 보류)로 거를 수 있습니다 (`?cause=inspection`).
- 하위 LOT으로 함께 보류된 LOT은 원인 LOT이 따로 나옵니다. LOT 번호를 누르면 추적 화면으로 가서 해제합니다.
- 원인 LOT과 검사 불합격 여부는 `lot_history`의 보류 메모("사유 (원인 LOT X)", "검사 불합격: ...")에서 읽습니다. 메모 형식은 `LotHoldService` 한 곳에서 만들고 읽습니다.

## 모니터링 화면

http://localhost:8080/monitoring 에서 CNC01~05의 현재 상태, 진행 중인 정지 사유, 마지막 센서값, 오늘 OEE를 실시간으로 보여 줍니다.

- 화면(`templates/monitoring.html`)은 빈 틀이고, `static/js/monitoring.js`가 데이터를 받아 카드를 그립니다. 화면 기술을 바꿔도 API는 그대로 씁니다.
- 실시간: 화면이 WebSocket(STOMP) `/ws`에 접속해 `/topic/dashboard`를 구독합니다. 설비 메시지가 저장되거나 집계가 끝나면 서버가 1초 안에 대시보드를 보냅니다 (`DashboardPusher`, 바뀐 것이 없으면 보내지 않음).
- 연결이 끊기면 화면 오른쪽 위가 "연결 끊김"으로 바뀌고, 다시 접속될 때까지 `GET /api/monitoring/dashboard`를 5초마다 조회합니다.
- 확인 전 알람이 있는 설비는 카드가 빨갛게 표시되고, [확인]을 누르면 사라집니다.

http://localhost:8080/monitoring/pareto 는 정지 사유 파레토입니다. 기간·설비를 고르면 사유별 정지 시간(막대)과 누적 비율(선)을 보여 주고, 누적 80%까지를 중점 관리 대상으로 표시합니다. 예: `?from=2025-10-01&to=2025-10-31` (부트캠프 실적)

## POP(현장 단말) API

현장 POP([pop/README.md](../pop/README.md))가 부르는 API입니다. 작업자는 로그인 세션 없이 사번(`workerId`)을 요청마다 보냅니다.

1. `GET /api/workers/{workerId}`로 작업자를 확인합니다 (없거나 퇴사면 404 `WORKER_NOT_FOUND`).
2. `GET /api/work-centers/{wcCode}/operations`로 작업장의 대기·진행 중인 공정을 받습니다. 앞 공정 양품 수량(`prevGoodQty`)이 함께 옵니다.
3. 공정 시작 → 실적 등록(여러 번) → 공정 완료. 첫 공정을 시작하면 작업지시가 진행이 되고, 마지막 공정을 완료하면 작업지시가 완료됩니다.
4. 실적에는 POP가 만든 `requestId`를 붙입니다. 같은 `requestId`로 다시 보내면 새로 저장하지 않고 처음 실적을 돌려줍니다 (`work_result.request_id` 필터 UNIQUE 인덱스).
5. 실적은 고치지 않고 취소 표시 후 새로 등록합니다. 취소하면 공정 합계에서 빠지고, 마감한 작업지시는 취소할 수 없습니다.
6. 설비를 적은 실적을 등록·취소하면 그날 일별 집계를 다시 합니다 (불량 수량 → OEE 품질).
7. 정지 사유: `GET /api/equipment/{equipCode}/downtimes`로 설비가 남긴 최근 정지를 받고, `PUT /api/downtimes/{downtimeId}/reason`으로 작업자가 대분류·소분류를 확정합니다. 소분류는 그 대분류에 속해야 하고(공통코드 `parent_code`), 확정한 작업자와 시각(`reason_at`)이 남습니다. 모니터링 화면과 정지 사유 파레토는 확정한 사유로 바로 바뀝니다.

8. 실적에는 투입한 LOT(`inputs`)을 함께 보냅니다. 아래 LOT 추적을 참고하세요.

이미 만든 DB에는 `db/ddl/07_work_result_request.sql`, `db/ddl/08_downtime_reason.sql`, `db/ddl/09_lot_tracking.sql`, `db/ddl/10_routing_lot_size.sql`, `db/ddl/11_inspection.sql`, `db/scripts/add_workers.sql`(작업자 7명)을 한 번 실행합니다.

## LOT 추적

1. 원자재 입고: `POST /api/lots/receive`로 LOT 관리 원자재(환봉, 키홈 핀)를 입고하면 LOT이 생깁니다. 번호는 `품번-yyMMdd-001` (예: `RM-SCM-001-261018-001`).
2. 실적 등록: 공정에 투입할 품목마다 LOT과 수량을 보냅니다. `GET /api/operations/{woOpId}/input-lots`가 투입할 품목과 쓸 수 있는 LOT(먼저 들어온 것부터)을 알려 줍니다.
   - 첫 공정은 BOM의 LOT 관리 자재(`item.lot_yn = Y`), 그다음 공정은 같은 작업지시의 앞 공정에서 만든 LOT을 받습니다. 절삭유·연삭석 같은 소모품은 고르지 않습니다.
   - 품목마다 투입 수량의 합이 (양품 + 불량) × 1개당 투입 수량과 같아야 합니다.
   - 서버는 투입 LOT을 줄이고(0이 되면 `CONSUMED`), 양품 수량으로 산출 LOT을 만들고, 투입 → 산출을 `lot_genealogy`에 잇습니다. 실적과 같은 트랜잭션이라 하나라도 틀리면 실적도 저장되지 않습니다.
   - 투입 LOT은 `UPDLOCK`으로 잠그고 읽어, 두 단말이 같은 LOT을 동시에 줄이지 못하게 합니다.
   - 포장 분할: 라우팅에 LOT 크기(`routing.lot_size`)가 있으면 양품을 그 크기씩 여러 LOT으로 나눕니다. 조립·포장(OP-70)은 20구 트레이라 20입니다. 예: 양품 45 → 20, 20, 5. 응답의 `outputLotNos`에 모두 나옵니다.
   - 나눌 때 계보는 투입 LOT을 적은 순서대로 꺼내 산출 LOT을 앞에서부터 채웁니다. 불량 몫은 마지막 LOT에 붙어, 투입 LOT마다 이은 수량의 합이 투입 수량과 같습니다.
3. 실적 취소: 만든 LOT(나눴으면 모두)은 폐기(`SCRAPPED`)하고 투입 수량을 돌려줍니다. 만든 LOT을 이미 다음 공정에 썼으면 409 `LOT_IN_USE` (뒤 공정의 실적부터 취소).
4. 추적: `GET /api/lots/{lotNo}/trace`가 재귀 CTE로 역방향(이 LOT을 만든 재료, 원자재까지)과 정방향(이 LOT이 들어간 곳)을 함께 돌려줍니다. 취소한 실적의 계보는 뺍니다.
5. 이력: `GET /api/lots/{lotNo}`에 입고·생산·투입·취소·보류 이력이 일어난 순서대로 나옵니다.
6. 품질 보류: `POST /api/lots/{lotNo}/hold`, `POST /api/lots/{lotNo}/release` (`{"workerId":"W6001","reason":"외경 치수 불량 의심","includeDownstream":true}`).
   - 품질 담당(QC)·관리자(ADMIN)만 할 수 있습니다. 작업자는 403 `NOT_ALLOWED`.
   - `includeDownstream`이면 정방향 추적으로 이 LOT이 들어간 하위 LOT까지 한꺼번에 바꿉니다.
   - 남은 수량이 있는(`ACTIVE`) LOT만 `HOLD`가 됩니다. 다 쓴·폐기·출하 LOT은 응답의 `skipped`에 상태와 함께 나옵니다. 해제는 `HOLD`인 LOT만 `ACTIVE`로 돌립니다.
   - 바꾼 LOT마다 `lot_history`에 `HOLD`·`RELEASE`와 사유를 남깁니다. 하위 LOT의 사유에는 원인 LOT 번호가 붙습니다.
   - 보류한 LOT은 투입 후보에서 빠지고, 투입하면 409 `LOT_ON_HOLD`입니다. 보류 LOT을 만든 실적은 취소할 수 없습니다(먼저 해제). 보류 LOT에 투입 수량을 되돌릴 때는 보류를 유지합니다.
   - 대상 LOT을 잠근(`UPDLOCK`) 뒤 정방향 추적을 다시 해, 그 사이 다른 실적이 만든 하위 LOT도 빠뜨리지 않습니다.

http://localhost:8080/lots/trace 는 LOT 추적 화면입니다.

- LOT 번호를 넣거나, 품목을 골라 최근 LOT 목록에서 누릅니다. `?lotNo=SF-SFT-030-261019-001`처럼 주소에 LOT을 붙이면 바로 열립니다 (메신저로 링크를 보낼 수 있음).
- LOT 카드, 회수 범위 숫자(들어간 원자재 LOT, 이 LOT이 들어간 LOT, 그중 아직 쓸 수 있는 LOT, 보류 중인 LOT), 역방향·정방향 트리, 이력을 보여 줍니다.
- LOT 카드의 품질 보류 칸에 사번(예: W6001)과 사유를 넣고 [보류]·[보류 해제]를 누릅니다. 하위 LOT 포함은 기본으로 켜져 있습니다. 끝나면 바꾼 LOT 수와 건너뛴 LOT(소진 n개 등)을 보여 주고 다시 추적합니다.
- 트리의 LOT 번호를 누르면 그 LOT으로 다시 추적하고, 브라우저 [뒤로]로 돌아갑니다.
- 화면은 모니터링 화면처럼 빈 틀(`templates/lot-trace.html`)이고, `static/js/lot-trace.js`가 두 API를 불러 그립니다.

LOT 추적을 넣기 전에 시작한 작업지시는 앞 공정 LOT이 없어 두 번째 공정부터 실적을 등록할 수 없습니다. 새 작업지시로 확인합니다.

## 검사

LOT을 만든 공정(`lot.last_process`)의 검사 항목(`inspection_spec`)을 시료 여러 개로 재고, 서버가 하한·상한으로 판정합니다. 입력은 POP의 [검사] 탭에서 합니다.

1. 검사표: `GET /api/lots/{lotNo}/inspection` → 검사 항목(하한·상한·단위)과, 이미 검사했으면 시료별 측정값과 판정. 검사 항목이 없는 공정(세척, 포장)과 원자재 LOT은 `items`가 빕니다.
2. 등록: `POST /api/lots/{lotNo}/inspection` (`{"workerId":"W1001","measurements":[{"inspectCode":"OD","sampleNo":1,"value":38.01}, ...]}`)
   - 모든 항목을 시료 1번부터 n번까지(최대 10) 빠짐없이 보내야 합니다. 빠지거나, 다른 공정 항목이거나, 같은 시료를 두 번 적으면 400.
   - 값마다 하한 이상·상한 이하(경계 포함)면 OK, 아니면 NG. 한쪽 한계만 있는 항목(표면 거칠기는 상한만)은 다른 쪽을 보지 않습니다.
   - 하나라도 NG면 LOT이 불합격이고, 같은 트랜잭션에서 이 LOT과 하위 LOT을 보류합니다(위 품질 보류와 같은 방식, 역할 확인 없음). 해제는 QC·ADMIN만 합니다.
   - `lot_history`에 `INSPECT`(합격 또는 "불합격: 외경 #2 38.07 (상한 38.05)")를 남깁니다.
   - LOT 하나는 공정마다 한 번 검사합니다. 같은 값을 다시 보내면 처음 결과를 돌려주고(응답을 못 받은 POP의 재전송), 다른 값이면 409 `INSPECTION_DONE`. `inspection_result`에 (LOT, 공정, 항목, 시료) UNIQUE 인덱스가 있습니다.
3. 추적 화면(`/lots/trace`)에 검사 결과 표가 나옵니다. 기준을 벗어난 값은 빨갛게 보입니다.

## 진동·온도 관리한계 알람 (CBM)

1. 고장 징후가 없던 기간의 가동 데이터로 관리한계를 정합니다: `POST /api/equipment-limits/calculate?from=2026-10-16&to=2026-10-19`
   - 설비·항목(진동, 온도)마다 UCL = 평균 + 3σ를 `equipment_limit`에 저장합니다. 표본이 30개 미만인 설비는 건너뜁니다.
   - 자동으로 다시 계산하지 않습니다. 설비가 나빠지는 중에 한계를 새 데이터로 고치면 한계도 같이 올라가기 때문입니다.
2. 그 뒤로 가동(RUN) 중 메시지의 값이 UCL을 넘으면 `equipment_alarm`에 남깁니다. 같은 설비·종류의 확인 전 알람이 있으면 새로 만들지 않습니다.
3. 대시보드의 [확인] 또는 `POST /api/alarms/{alarmId}/ack`로 확인합니다.

이미 만든 DB에는 `db/ddl/06_equipment_limit.sql`을 한 번 실행해야 합니다 (없을 때만 테이블을 만듭니다).
- "지금"과 "오늘"은 공장 시계(설비가 보낸 가장 늦은 측정 시각) 기준입니다. 30분 넘게 소식이 없는 설비는 "통신 없음"으로 표시합니다.

## 일별 생산 실적과 OEE

수집 데이터로 `production_daily`(source `MES`)를 만들면, 기존 OEE 뷰(`v_oee_daily`, `v_oee_monthly`)가 부트캠프 실적과 같은 식으로 계산합니다.

| 항목 | 계산 |
| --- | --- |
| 생산 수량 | 그날 메시지의 누적 카운트(`cycleCount`)가 늘어난 양의 합. 카운트가 줄면(시뮬레이터 재시작) 새 값을 더함 |
| 계획 시간 | 그날 상태 이력이 덮는 시간 (설비 데이터가 있었던 시간) |
| 비가동 시간 | 그중 DOWN, SETUP 시간 |
| 불량 | 그날 POP에서 그 설비로 등록한 불량의 합 (취소한 실적 제외, 실적 등록 시각 기준). 생산 수량보다 많으면 경고를 남기고 생산 수량까지만 |
| 양품 | 생산 수량 − 불량 |

- `ProductionAggregationScheduler`가 1분마다, 새 데이터가 들어온 날짜와 POP 실적이 바뀐 날짜만 다시 집계합니다 (`mes.aggregation.*`).
- 부트캠프 실적이 있는 날짜는 덮어쓰지 않습니다.
- 시각은 모두 공장 시계(시뮬레이터가 보낸 측정 시각) 기준입니다. 600배속이면 현실 1분에 공장 시계 10시간이 흐릅니다.
- POP 실적의 시각은 서버 시계입니다. 실제 공장에서는 두 시계가 같지만, 시뮬레이터를 배속으로 돌리면 공장 시계가 앞서 나가 실적이 다른 날짜에 들어갈 수 있습니다.

## 설비 데이터 수집 (MQTT)

```
시뮬레이터(C#) ──QoS 1──▶ Mosquitto ──QoS 1──▶ MqttCollector ──▶ EquipmentDataService ──▶ equipment_data
```

| 상황 | 동작 |
| --- | --- |
| 서버가 꺼져 있음 | 영속 세션(`cleanSession=false`, 고정 client-id)이라 브로커가 메시지를 보관하고, 서버가 다시 접속하면 보냅니다. |
| 브로커가 꺼져 있음 | 시뮬레이터가 로컬 버퍼에 쌓아 두고, 서버는 지수 백오프로 재접속합니다. 서버 API는 브로커 없이도 뜹니다. |
| 같은 메시지가 두 번 옴 | `messageId`로 걸러 한 번만 저장합니다 (UNIQUE 제약이 2차 방어). |
| DB 저장 실패 | 수신 확인을 보내지 않아 브로커가 다시 보냅니다. |
| 깨진 JSON, 모르는 설비 | 로그를 남기고 버립니다. |

상태가 바뀌는 메시지(예: RUN → DOWN)가 오면 이력도 함께 남깁니다.

| 테이블 | 언제 | 내용 |
| --- | --- | --- |
| `equipment_status_log` | 상태가 바뀔 때마다 | 앞 상태 행을 닫고(`ended_at`) 새 상태 행을 엽니다. `ended_at`이 비어 있는 행이 현재 상태입니다. |
| `downtime` (`source = 'EQUIP'`) | DOWN·SETUP이 시작될 때 열고, 끝날 때 닫음 | 정지 사유 코드(`dt_cat`, `dt_sub`)와 정지 시간(`duration_min`, 분 단위 반올림) |
| `equipment.status` | 상태가 바뀔 때마다 | 설비의 현재 상태 |

30분 넘게 소식이 없던 설비에서 메시지가 오면(시뮬레이터를 껐다 켬, 통신 끊김), 그 사이는 어떤 상태로도 기록하지 않습니다. 시뮬레이터는 정지 중에도 10분마다 상태를 보냅니다.

설정은 `application.yml`의 `mes.mqtt.*`에 있습니다. 테스트 프로필은 `mes.mqtt.enabled: false`로 수집기를 끄고 실행합니다.
저장 내용을 한 건씩 보려면 `logging.level.com.minimes.equipment`를 `debug`로 바꿉니다.

## 테스트

| 명령 | 내용 | Docker |
| --- | --- | --- |
| `./gradlew test` | 단위 테스트 + H2 메모리 DB 테스트 | 필요 없음 |
| `./gradlew integrationTest` | MSSQL 컨테이너에 `db/ddl`, `db/seed`를 적용한 뒤 API 호출 | 필요 |

IntelliJ에서는 테스트 클래스 옆의 ▶ 버튼으로 하나씩 실행할 수 있습니다.

## 패키지 구조

기능(도메인) 단위로 나누고, 각 기능 안에서 계층별로 나눴습니다.

```
com.minimes
├─ common
│  ├─ config      Clock, 스케줄러, WebSocket(STOMP) 공통 설정
│  └─ error       ErrorCode, BusinessException, 공통 오류 응답, 전역 예외 처리
├─ master         기준정보 (조회)
│  ├─ domain      엔티티: Item, Bom, Routing, Equipment, CommonCode, Worker ...
│  ├─ repository  Spring Data JPA
│  ├─ mapper      MyBatis (BOM 재귀 쿼리) → resources/mapper/master/BomMapper.xml
│  ├─ service
│  └─ web         REST 컨트롤러, 응답 DTO
├─ workorder      작업지시 (생성, 상태 변경), 공정 작업과 실적 (POP)
│  ├─ domain      WorkOrder·WorkOrderOperation(상태 규칙), WorkResult(실적, 취소 표시), Defect(불량 내역)
│  ├─ repository
│  ├─ mapper      WorkListMapper (작업장의 작업 목록 + 앞 공정 양품) → resources/mapper/workorder/
│  ├─ service     WorkOrderService, OperationService (시작·실적·완료·취소, requestId 중복 방지)
│  └─ web         WorkOrderController, OperationController (POP API), WorkOrderPageController (/work-orders 화면)
├─ lot            LOT 입고, 실적의 투입·산출, 취소 되돌리기, 추적, 품질 보류
│  ├─ domain      Lot(남은 수량, 상태 규칙), LotGenealogy(투입 → 산출), LotHistory(이력), LotStatus, LotEventType
│  ├─ repository  LotRepository (투입 LOT 잠금 조회 포함)
│  ├─ mapper      LotTraceMapper (정방향·역방향 재귀 CTE) → resources/mapper/lot/LotTraceMapper.xml
│  ├─ service     LotService (공정에 투입할 품목, 투입 확인, 기록, 취소), LotHoldService (보류·해제)
│  └─ web         LotController (/api/lots, 보류·해제, 공정 투입 LOT), LotPageController (/lots/trace, /lots/hold 화면)
├─ quality        검사 결과와 판정, 불합격 자동 보류
│  ├─ domain      InspectionResult(시료 하나의 측정값과 판정), Judgement
│  ├─ repository
│  ├─ service     InspectionService (검사표, 빠짐 확인, 판정, 재전송 처리, 불합격이면 LotHoldService.holdByRule)
│  └─ web         InspectionController (/api/lots/{lotNo}/inspection)
├─ production     일별 생산 실적 집계, OEE 조회
│  ├─ domain      ProductionDaily (production_daily, 복합키)
│  ├─ repository
│  ├─ mapper      OeeMapper (v_oee_daily, v_oee_monthly) → resources/mapper/production/OeeMapper.xml
│  ├─ service     ProductionAggregationService (집계, POP 불량 포함), ProductionAggregationScheduler (1분마다 + 실적이 바뀐 날짜)
│  └─ web         OeeController
├─ alarm          관리한계(UCL) 계산, 알람 기록·확인
│  ├─ domain      EquipmentLimit (equipment_limit), EquipmentAlarm (equipment_alarm), LimitMetric, AlarmType
│  ├─ repository
│  ├─ mapper      SensorStatsMapper (가동 데이터 평균·표준편차) → resources/mapper/alarm/
│  ├─ service     AlarmService (저장 이벤트 → 한계 비교), LimitCalculationService (UCL = 평균 + 3σ)
│  └─ web         AlarmController (/api/alarms, /api/equipment-limits)
├─ monitoring     설비 현황 대시보드, 정지 사유 파레토
│  ├─ push        DashboardPusher (저장·집계·사유 확정 이벤트 → 1초마다 /topic/dashboard로 전송)
│  ├─ mapper      MonitoringMapper (설비별 최신 상태, OUTER APPLY) → resources/mapper/monitoring/
│  ├─ service     MonitoringService (공장 시계 기준 판단), DowntimeParetoService (비율, 80% 표시)
│  └─ web         MonitoringController (/api/monitoring), MonitoringPageController (/monitoring, /monitoring/pareto 화면)
└─ equipment      설비 데이터 수집
   ├─ domain      EquipmentData(원본), EquipmentStatusLog(상태 이력), Downtime(비가동)
   ├─ repository
   ├─ service     EquipmentDataService (중복 확인, 저장), EquipmentStatusService (상태 전환 기록), DowntimeService (POP 정지 사유 확정)
   ├─ web         DowntimeController (POP 정지 목록, 사유 확정)
   └─ mqtt        MqttCollector (구독, 재접속), EquipmentMessage, MqttProperties
```

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/items?type=FG` | 품목 목록 (type 생략 가능) |
| GET | `/api/items/{itemCode}` | 품목 단건 |
| GET | `/api/items/{itemCode}/routings` | 라우팅 |
| GET | `/api/items/{itemCode}/bom-tree` | BOM 전개 |
| GET | `/api/equipment` | 설비 목록 |
| GET | `/api/common-codes/{groupCode}` | 공통코드 |
| POST | `/api/work-orders` | 작업지시 생성 |
| GET | `/api/work-orders?status=RUN` | 작업지시 목록 (공정마다 `processName` 포함) |
| GET | `/api/work-orders/{woNo}` | 작업지시 단건 |
| POST | `/api/work-orders/{woNo}/start` | 시작 (대기 → 진행) |
| POST | `/api/work-orders/{woNo}/complete` | 완료 (진행 → 완료) |
| POST | `/api/work-orders/{woNo}/close` | 마감 (완료 → 마감) |
| GET | `/api/workers/{workerId}` | 작업자 확인 (POP 로그인) |
| GET | `/api/work-centers/{wcCode}/operations` | 작업장의 대기·진행 중인 공정 (POP 작업 목록) |
| POST | `/api/operations/{woOpId}/start` | 공정 시작 (`{"workerId":"W1001"}`) |
| POST | `/api/operations/{woOpId}/results` | 실적 등록 (`requestId`, `workerId`, `equipCode`, `goodQty`, `defects[]`, `inputs[]`). 응답에 만든 LOT(`outputLotNos`, 포장은 여러 개) |
| GET | `/api/operations/{woOpId}/input-lots` | 공정에 투입할 품목과 쓸 수 있는 LOT (먼저 들어온 것부터) |
| GET | `/api/operations/{woOpId}/results` | 공정의 실적 목록 (취소 포함) |
| POST | `/api/operations/{woOpId}/complete` | 공정 완료 (마지막 공정이면 작업지시도 완료) |
| POST | `/api/results/{resultId}/cancel` | 실적 취소 (`{"workerId":"W1001","reason":"수량 오입력"}`) |
| GET | `/api/equipment/{equipCode}/downtimes?limit=10` | 설비가 남긴 최근 정지 (POP 정지 사유 입력) |
| PUT | `/api/downtimes/{downtimeId}/reason` | 정지 사유 확정 (`{"workerId":"W1001","dtCat":"MATERIAL","dtSub":"MAT_NOT_RECEIVED","note":"..."}`) |
| POST | `/api/lots/receive` | 원자재 입고 (`{"itemCode":"RM-SCM-001","qty":100,"supplierLotNo":"A-1018","workerId":"W1001"}`) |
| GET | `/api/lots?itemCode=RM-SCM-001&status=ACTIVE` | 품목의 LOT 목록 (최근 100개) |
| GET | `/api/lots/held` | 보류 중인 LOT과 마지막 보류 (사유, 원인 LOT, 검사 불합격인가) |
| GET | `/api/lots/{lotNo}` | LOT과 이력 (공정 이름 포함) |
| GET | `/api/lots/{lotNo}/trace` | 역방향(`backward`)·정방향(`forward`) 추적 |
| POST | `/api/lots/{lotNo}/hold` | 품질 보류 (`{"workerId":"W6001","reason":"...","includeDownstream":true}`). QC·ADMIN만 |
| POST | `/api/lots/{lotNo}/release` | 보류 해제 (요청 형식은 보류와 같음) |
| GET | `/api/lots/{lotNo}/inspection` | 검사표: LOT을 만든 공정의 검사 항목과 측정값·판정 |
| POST | `/api/lots/{lotNo}/inspection` | 검사 등록 (`{"workerId":"W1001","measurements":[{"inspectCode":"OD","sampleNo":1,"value":38.01}]}`). NG면 자동 보류 |
| GET | `/api/monitoring/dashboard` | 설비 현황 (현재 상태, 정지 사유, 센서값, 오늘 OEE) |
| GET | `/api/oee/daily?date=2025-10-01` | 설비별 일별 OEE와 근거 숫자 |
| GET | `/api/oee/monthly?month=2025-10` | 설비별 월별 OEE (시간·수량 합산 후 계산) |
| POST | `/api/production-daily/aggregate?date=2026-10-17` | 그날 집계 다시 하기 |
| GET | `/api/monitoring/downtime-pareto?from=2025-10-01&to=2025-10-31&equipCode=CNC03&byReason=true` | 정지 사유 파레토 (모든 조건 생략 가능) |
| GET | `/api/alarms?open=true` | 알람 목록 (open=false면 최근 100건) |
| POST | `/api/alarms/{alarmId}/ack` | 알람 확인 (`{"ackBy":"kim"}`) |
| GET | `/api/equipment-limits` | 설비별 관리한계 |
| POST | `/api/equipment-limits/calculate?from=&to=` | 관리한계 계산 (기준 기간 가동 데이터, UCL = 평균 + 3σ) |
| WebSocket | `/ws` → 구독 `/topic/dashboard` | 대시보드 실시간 수신 (STOMP, 본문은 `/api/monitoring/dashboard`와 같음) |

오류 응답은 항상 같은 형식입니다.

```json
{"code": "INVALID_STATUS_TRANSITION", "message": "WAIT 상태의 작업지시는 마감할 수 없습니다. (DONE 상태에서만 가능)", "fieldErrors": []}
```
