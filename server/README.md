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

## 모니터링 화면

http://localhost:8080/monitoring 에서 CNC01~05의 현재 상태, 진행 중인 정지 사유, 마지막 센서값, 오늘 OEE를 실시간으로 보여 줍니다.

- 화면(`templates/monitoring.html`)은 빈 틀이고, `static/js/monitoring.js`가 데이터를 받아 카드를 그립니다. 화면 기술을 바꿔도 API는 그대로 씁니다.
- 실시간: 화면이 WebSocket(STOMP) `/ws`에 접속해 `/topic/dashboard`를 구독합니다. 설비 메시지가 저장되거나 집계가 끝나면 서버가 1초 안에 대시보드를 보냅니다 (`DashboardPusher`, 바뀐 것이 없으면 보내지 않음).
- 연결이 끊기면 화면 오른쪽 위가 "연결 끊김"으로 바뀌고, 다시 접속될 때까지 `GET /api/monitoring/dashboard`를 5초마다 조회합니다.
- 확인 전 알람이 있는 설비는 카드가 빨갛게 표시되고, [확인]을 누르면 사라집니다.

http://localhost:8080/monitoring/pareto 는 정지 사유 파레토입니다. 기간·설비를 고르면 사유별 정지 시간(막대)과 누적 비율(선)을 보여 주고, 누적 80%까지를 중점 관리 대상으로 표시합니다. 예: `?from=2025-10-01&to=2025-10-31` (부트캠프 실적)

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
| 양품·불량 | 아직 입력하는 곳이 없어 모두 양품 (POP 검사 입력 예정) |

- `ProductionAggregationScheduler`가 1분마다, 새 데이터가 들어온 날짜만 다시 집계합니다 (`mes.aggregation.*`).
- 부트캠프 실적이 있는 날짜는 덮어쓰지 않습니다.
- 시각은 모두 공장 시계(시뮬레이터가 보낸 측정 시각) 기준입니다. 600배속이면 현실 1분에 공장 시계 10시간이 흐릅니다.

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
│  ├─ domain      엔티티: Item, Routing, Equipment, CommonCode ...
│  ├─ repository  Spring Data JPA
│  ├─ mapper      MyBatis (BOM 재귀 쿼리) → resources/mapper/master/BomMapper.xml
│  ├─ service
│  └─ web         REST 컨트롤러, 응답 DTO
├─ workorder      작업지시 (생성, 상태 변경)
│  ├─ domain      WorkOrder(상태 규칙), WorkOrderOperation
│  ├─ repository
│  ├─ service
│  └─ web
├─ production     일별 생산 실적 집계, OEE 조회
│  ├─ domain      ProductionDaily (production_daily, 복합키)
│  ├─ repository
│  ├─ mapper      OeeMapper (v_oee_daily, v_oee_monthly) → resources/mapper/production/OeeMapper.xml
│  ├─ service     ProductionAggregationService (집계), ProductionAggregationScheduler (1분마다)
│  └─ web         OeeController
├─ alarm          관리한계(UCL) 계산, 알람 기록·확인
│  ├─ domain      EquipmentLimit (equipment_limit), EquipmentAlarm (equipment_alarm), LimitMetric, AlarmType
│  ├─ repository
│  ├─ mapper      SensorStatsMapper (가동 데이터 평균·표준편차) → resources/mapper/alarm/
│  ├─ service     AlarmService (저장 이벤트 → 한계 비교), LimitCalculationService (UCL = 평균 + 3σ)
│  └─ web         AlarmController (/api/alarms, /api/equipment-limits)
├─ monitoring     설비 현황 대시보드, 정지 사유 파레토
│  ├─ push        DashboardPusher (저장·집계 이벤트 → 1초마다 /topic/dashboard로 전송)
│  ├─ mapper      MonitoringMapper (설비별 최신 상태, OUTER APPLY) → resources/mapper/monitoring/
│  ├─ service     MonitoringService (공장 시계 기준 판단), DowntimeParetoService (비율, 80% 표시)
│  └─ web         MonitoringController (/api/monitoring), MonitoringPageController (/monitoring, /monitoring/pareto 화면)
└─ equipment      설비 데이터 수집
   ├─ domain      EquipmentData(원본), EquipmentStatusLog(상태 이력), Downtime(비가동)
   ├─ repository
   ├─ service     EquipmentDataService (중복 확인, 저장), EquipmentStatusService (상태 전환 기록)
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
| GET | `/api/work-orders?status=RUN` | 작업지시 목록 |
| GET | `/api/work-orders/{woNo}` | 작업지시 단건 |
| POST | `/api/work-orders/{woNo}/start` | 시작 (대기 → 진행) |
| POST | `/api/work-orders/{woNo}/complete` | 완료 (진행 → 완료) |
| POST | `/api/work-orders/{woNo}/close` | 마감 (완료 → 마감) |
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
