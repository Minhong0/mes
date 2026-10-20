package com.minimes.integration;

import com.jayway.jsonpath.JsonPath;
import com.minimes.equipment.mqtt.EquipmentMessage;
import com.minimes.equipment.service.EquipmentDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 MSSQL 통합 테스트. 실행: ./gradlew integrationTest (Docker 필요)
 *
 * 1) Testcontainers가 MSSQL 컨테이너를 새로 띄운다.
 * 2) 저장소의 db/ddl, db/seed 스크립트를 그대로 실행한다. → 운영과 같은 스키마와 데이터
 * 3) Spring을 ddl-auto: validate로 띄운다. → 엔티티와 실제 테이블 구조가 다르면 여기서 실패한다.
 * 4) H2에서는 확인할 수 없는 MSSQL 전용 쿼리(재귀 CTE, OPTION MAXRECURSION)를 API로 호출해 본다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class MssqlIntegrationTest {

    static final MSSQLServerContainer MSSQL =
            new MSSQLServerContainer(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                    .acceptLicense();

    // Spring 컨텍스트가 뜨기 전에 컨테이너를 띄우고 스키마와 데이터를 넣어야 하므로 static 블록에서 한다.
    static {
        MSSQL.start();
        Path db = Path.of("..", "db");   // 테스트는 server 폴더에서 실행된다
        try (Connection conn = DriverManager.getConnection(MSSQL.getJdbcUrl(), MSSQL.getUsername(), MSSQL.getPassword())) {
            SqlScriptRunner.runDirectory(conn, db.resolve("ddl"));
            SqlScriptRunner.runDirectory(conn, db.resolve("seed"));
        } catch (Exception e) {
            throw new IllegalStateException("MSSQL 초기화 실패", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MSSQL.getJdbcUrl() + ";databaseName=minimes");
        registry.add("spring.datasource.username", MSSQL::getUsername);
        registry.add("spring.datasource.password", MSSQL::getPassword);
        registry.add("mes.mqtt.enabled", () -> "false");
        registry.add("mes.aggregation.enabled", () -> "false");   // 이 테스트는 API와 스키마만 본다. 브로커 없이 실행
        registry.add("mes.monitoring.push.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("BOM 전개: 완제품 1개 → 11줄, 환봉은 Level 5에 1개")
    void bomTree() throws Exception {
        mockMvc.perform(get("/api/items/FG-SFT-100/bom-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[0].itemCode").value("FG-SFT-100"))
                .andExpect(jsonPath("$[0].bomLevel").value(0))
                .andExpect(jsonPath("$[?(@.itemCode == 'RM-SCM-001')].bomLevel").value(hasItem(5)))
                .andExpect(jsonPath("$[?(@.itemCode == 'RM-SCM-001')].totalQty").value(hasItem(1.0)))
                .andExpect(jsonPath("$[?(@.itemCode == 'RM-PKG-001')].totalQty").value(hasItem(0.05)));
    }

    @Test
    @DisplayName("라우팅: 7개 공정, 공정 이름이 한글로 붙는다")
    void routings() throws Exception {
        mockMvc.perform(get("/api/items/FG-SFT-100/routings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[2].processCode").value("OP-30"))
                .andExpect(jsonPath("$[2].processName").value("열처리"))
                .andExpect(jsonPath("$[2].timeBasis").value("BATCH"))
                .andExpect(jsonPath("$[2].lotSize").doesNotExist())
                .andExpect(jsonPath("$[6].lotSize").value(20));   // 조립·포장은 20개 트레이로 나눈다
    }

    @Test
    @DisplayName("설비 12대, CNC01은 자동 수집 대상")
    void equipment() throws Exception {
        mockMvc.perform(get("/api/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                // 설비코드 순 정렬이라 CMM-01(M)이 CNC01(N)보다 앞에 온다. 위치 대신 코드로 찾는다.
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC01')].autoCollect").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.equipCode == 'HTF-01')].autoCollect").value(hasItem(false)));
    }

    @Test
    @DisplayName("공통코드: 비가동 대분류 5종")
    void commonCodes() throws Exception {
        mockMvc.perform(get("/api/common-codes/DT_CAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[2].codeName").value("품번교체(셋업)"));
    }

    @Test
    @DisplayName("MSSQL에서 작업지시 생성 → 시작: 공정 7개가 IDENTITY 번호와 함께 저장된다")
    void workOrderFlow() throws Exception {
        String body = mockMvc.perform(post("/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode": "FG-SFT-100", "planQty": 200, "note": "통합 테스트"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operations.length()").value(7))
                .andReturn().getResponse().getContentAsString();
        String woNo = JsonPath.read(body, "$.woNo");

        mockMvc.perform(post("/api/work-orders/{woNo}/start", woNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUN"));

        mockMvc.perform(get("/api/work-orders/{woNo}", woNo))
                .andExpect(jsonPath("$.status").value("RUN"))
                .andExpect(jsonPath("$.note").value("통합 테스트"))
                .andExpect(jsonPath("$.operations[0].woOpId").isNumber());
    }

    @Test
    @DisplayName("월별 OEE (뷰 + MyBatis): 부트캠프 2025-10 정답값 CNC01 84.24%, CNC03 59.18%")
    void monthlyOee() throws Exception {
        mockMvc.perform(get("/api/oee/monthly").param("month", "2025-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC01')].oee").value(hasItem(84.24)))
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC03')].oee").value(hasItem(59.18)))
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC03')].availability").value(hasItem(76.61)));
    }

    @Test
    @DisplayName("일별 OEE: 근거 숫자(계산 컬럼 run_min 포함)와 비율이 함께 나온다")
    void dailyOee() throws Exception {
        mockMvc.perform(get("/api/oee/daily").param("date", "2025-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].equipCode").value("CNC01"))
                .andExpect(jsonPath("$[0].source").value("BOOTCAMP"))
                .andExpect(jsonPath("$[0].oee").value(80.00))
                .andExpect(jsonPath("$[0].runMin").isNumber());
    }

    @Test
    @DisplayName("집계 다시 하기: 부트캠프 실적이 있는 날은 덮어쓰지 않는다")
    void aggregateKeepsBootcamp() throws Exception {
        mockMvc.perform(post("/api/production-daily/aggregate").param("date", "2025-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("BOOTCAMP"))
                .andExpect(jsonPath("$[0].oee").value(80.00));
    }

    @Autowired
    EquipmentDataService equipmentDataService;

    @Test
    @DisplayName("모니터링 (OUTER APPLY + 공통코드 이름): CNC03 정지 10분째, 사유 '스핀들 이상', 메시지가 없는 설비는 통신 없음")
    void monitoringDashboard() throws Exception {
        sendCnc03("2026-11-01T08:00:00+09:00", 0, "RUN", null, null);
        sendCnc03("2026-11-01T08:18:00+09:00", 1, "RUN", null, null);
        sendCnc03("2026-11-01T08:30:00+09:00", 1, "DOWN", "BREAKDOWN", "BRK_SPINDLE");
        sendCnc03("2026-11-01T08:40:00+09:00", 1, "DOWN", "BREAKDOWN", "BRK_SPINDLE");   // heartbeat

        mockMvc.perform(get("/api/monitoring/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prodDate").value("2026-11-01"))
                .andExpect(jsonPath("$.equipment.length()").value(5))
                .andExpect(jsonPath("$.equipment[2].equipCode").value("CNC03"))
                .andExpect(jsonPath("$.equipment[2].online").value(true))
                .andExpect(jsonPath("$.equipment[2].status").value("DOWN"))
                .andExpect(jsonPath("$.equipment[2].statusName").value("정지"))
                .andExpect(jsonPath("$.equipment[2].statusMinutes").value(10))
                .andExpect(jsonPath("$.equipment[2].stop.dtCatName").value("설비고장"))
                .andExpect(jsonPath("$.equipment[2].stop.dtSubName").value("스핀들 이상"))
                .andExpect(jsonPath("$.equipment[2].cycleCount").value(1))
                .andExpect(jsonPath("$.equipment[0].equipCode").value("CNC01"))
                .andExpect(jsonPath("$.equipment[0].online").value(false));
    }

    private void sendCnc03(String collectedAt, int cycleCount, String status, String dtCat, String dtSub) {
        equipmentDataService.save(new EquipmentMessage("CNC03-IT-" + collectedAt, "CNC03", OffsetDateTime.parse(collectedAt),
                cycleCount, status, new BigDecimal("1.2"), new BigDecimal("35.0"), dtCat, dtSub));
    }

    @Test
    @DisplayName("정지 사유 파레토 (윈도 함수 누계): 부트캠프 2025-10, 220건 6,990분, 1위 지그 교체 979분 14.0%")
    void downtimePareto() throws Exception {
        mockMvc.perform(get("/api/monitoring/downtime-pareto").param("from", "2025-10-01").param("to", "2025-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMin").value(6990))
                .andExpect(jsonPath("$.stopCount").value(220))
                .andExpect(jsonPath("$.items[0].dtSubName").value("지그 교체"))
                .andExpect(jsonPath("$.items[0].totalMin").value(979))
                .andExpect(jsonPath("$.items[0].sharePct").value(14.0))
                .andExpect(jsonPath("$.items[-1].cumulativePct").value(100.0));

        mockMvc.perform(get("/api/monitoring/downtime-pareto")
                        .param("from", "2025-10-01").param("to", "2025-10-31").param("byReason", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dtCat").value("SETUP"))
                .andExpect(jsonPath("$.items[0].dtSub").doesNotExist());
    }

    @Test
    @DisplayName("관리한계 (STDEV): CNC04 가동 40건으로 UCL = 평균 + 3σ, 넘는 메시지 → 알람 → 대시보드에 표시 → 확인 두 번이면 409")
    void alarmLimit() throws Exception {
        double[] vibration = new double[40];
        for (int i = 0; i < 40; i++) {
            vibration[i] = 1.0 + (i % 6) * 0.1;
            sendCnc04(String.format("2026-10-20T%02d:%02d:00+09:00", 8 + i / 6, (i % 6) * 10), vibration[i], 35.0 + i % 4);
        }
        double mean = java.util.Arrays.stream(vibration).average().orElseThrow();
        double sigma = Math.sqrt(java.util.Arrays.stream(vibration).map(v -> (v - mean) * (v - mean)).sum() / 39);

        mockMvc.perform(post("/api/equipment-limits/calculate").param("from", "2026-10-20").param("to", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].equipCode").value("CNC04"))
                .andExpect(jsonPath("$[0].metric").value("VIBRATION"))
                .andExpect(jsonPath("$[0].sampleCount").value(40))
                .andExpect(jsonPath("$[0].ucl").value(closeTo(mean + 3 * sigma, 0.0006)));

        sendCnc04("2026-10-20T15:00:00+09:00", 2.5, 36.0);

        String alarms = mockMvc.perform(get("/api/alarms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipCode").value("CNC04"))
                .andExpect(jsonPath("$[0].alarmType").value("VIBRATION_UCL"))
                .andReturn().getResponse().getContentAsString();
        int alarmId = JsonPath.read(alarms, "$[0].alarmId");

        mockMvc.perform(get("/api/monitoring/dashboard"))
                .andExpect(jsonPath("$.equipment[3].equipCode").value("CNC04"))
                .andExpect(jsonPath("$.equipment[3].alarms[0].alarmType").value("VIBRATION_UCL"));

        mockMvc.perform(post("/api/alarms/{id}/ack", alarmId).contentType(MediaType.APPLICATION_JSON).content("{\"ackBy\":\"kim\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ackBy").value("kim"));
        mockMvc.perform(post("/api/alarms/{id}/ack", alarmId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALARM_ALREADY_ACKNOWLEDGED"));
    }

    private void sendCnc04(String collectedAt, double vibration, double temp) {
        equipmentDataService.save(new EquipmentMessage("CNC04-IT-" + collectedAt, "CNC04", OffsetDateTime.parse(collectedAt),
                1, "RUN", BigDecimal.valueOf(vibration), BigDecimal.valueOf(temp), null, null));
    }

    @Test
    @DisplayName("POP 흐름: 작업지시 생성 → 작업 목록 → 시작 → 실적(같은 요청 두 번) → 취소 → 완료, 입력 오류는 400")
    void popFlow() throws Exception {
        String created = mockMvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\":\"FG-SFT-100\",\"planQty\":20}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String woNo = JsonPath.read(created, "$.woNo");
        int op10 = JsonPath.read(created, "$.operations[0].woOpId");

        mockMvc.perform(get("/api/workers/W1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerName").value("김도윤"))
                .andExpect(jsonPath("$.wcCode").value("WC-100"));
        mockMvc.perform(get("/api/workers/W0000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKER_NOT_FOUND"));

        mockMvc.perform(get("/api/work-centers/WC-100/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.woNo == '" + woNo + "')].processName").value(hasItem("CNC 선삭")));

        mockMvc.perform(post("/api/operations/{id}/start", op10).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUN"));

        String bar = receiveBar(10);
        String report = "{\"requestId\":\"it-req-1\",\"workerId\":\"W1001\",\"equipCode\":\"CNC01\",\"goodQty\":8,"
                + "\"defects\":[{\"defectType\":\"OVERSIZE\",\"qty\":2}],\"inputs\":[{\"lotNo\":\"" + bar + "\",\"qty\":10}]}";
        String first = mockMvc.perform(post("/api/operations/{id}/results", op10).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defects[0].defectType").value("OVERSIZE"))
                .andReturn().getResponse().getContentAsString();
        int resultId = JsonPath.read(first, "$.resultId");
        mockMvc.perform(post("/api/operations/{id}/results", op10).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(jsonPath("$.resultId").value(resultId));   // 다시 보내도 같은 실적

        mockMvc.perform(post("/api/operations/{id}/results", op10).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"it-req-2\",\"workerId\":\"W1001\",\"goodQty\":-1,\"defects\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/results/{id}/cancel", resultId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\",\"reason\":\"수량 오입력\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));

        mockMvc.perform(get("/api/work-orders/{woNo}", woNo))
                .andExpect(jsonPath("$.status").value("RUN"))
                .andExpect(jsonPath("$.operations[0].goodQty").value(0))
                .andExpect(jsonPath("$.operations[0].defectQty").value(0));

        mockMvc.perform(post("/api/operations/{id}/complete", op10).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("POP B (JPQL SUM, reason_at 컬럼): 불량이 OEE 품질에 반영되고, 작업자가 확정한 정지 사유가 파레토에 나온다")
    void popQualityAndDowntimeReason() throws Exception {
        // CNC02: 2026-10-25 08:00부터 12분마다 1개씩 6개, 09:12~09:22 정지(센서 불량)
        for (int i = 0; i <= 6; i++) {
            sendCnc02("2026-10-25T%02d:%02d:00+09:00".formatted(8 + i * 12 / 60, i * 12 % 60), i, "RUN", null, null);
        }
        sendCnc02("2026-10-25T09:12:30+09:00", 6, "DOWN", "BREAKDOWN", "BRK_SENSOR");
        sendCnc02("2026-10-25T09:22:30+09:00", 6, "RUN", null, null);

        // POP 실적: 실적의 시각은 서버 시계라 테스트가 정할 수 없어, 그날 실적을 직접 넣는다
        String created = mockMvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\":\"FG-SFT-100\",\"planQty\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int op10 = JsonPath.read(created, "$.operations[0].woOpId");
        String insert = """
                INSERT INTO work_result (wo_op_id, equip_code, worker_id, started_at, ended_at, good_qty, defect_qty, source,
                                         cancelled, cancelled_at, request_id)
                VALUES (?, 'CNC02', 'W1001', '2026-10-25 08:00', ?, ?, ?, 'POP', ?, ?, ?)
                """;
        jdbcTemplate.update(insert, op10, "2026-10-25 09:00", 4, 2, false, null, "it-q-1");
        jdbcTemplate.update(insert, op10, "2026-10-25 09:05", 0, 3, true, "2026-10-25 09:06", "it-q-2");   // 취소

        mockMvc.perform(post("/api/production-daily/aggregate").param("date", "2026-10-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC02')].prodQty").value(hasItem(6)))
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC02')].goodQty").value(hasItem(4)))
                .andExpect(jsonPath("$[?(@.equipCode == 'CNC02')].quality").value(hasItem(66.67)));

        String downtimes = mockMvc.perform(get("/api/equipment/CNC02/downtimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dtSub").value("BRK_SENSOR"))
                .andExpect(jsonPath("$[0].durationMin").value(10))
                .andExpect(jsonPath("$[0].reasonAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int downtimeId = JsonPath.read(downtimes, "$[0].downtimeId");

        mockMvc.perform(put("/api/downtimes/{id}/reason", downtimeId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\",\"dtCat\":\"MATERIAL\",\"dtSub\":\"BRK_SENSOR\"}"))
                .andExpect(status().isBadRequest());   // 소분류가 대분류에 속하지 않는다
        mockMvc.perform(put("/api/downtimes/{id}/reason", downtimeId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\",\"dtCat\":\"MATERIAL\",\"dtSub\":\"MAT_NOT_RECEIVED\",\"note\":\"소재 입고 지연\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerId").value("W1001"))
                .andExpect(jsonPath("$.reasonAt").isNotEmpty());

        mockMvc.perform(get("/api/monitoring/downtime-pareto")
                        .param("from", "2026-10-25").param("to", "2026-10-25").param("equipCode", "CNC02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].dtSubName").value("원자재 미입고"))
                .andExpect(jsonPath("$.items[0].totalMin").value(10));
    }

    private void sendCnc02(String collectedAt, int cycleCount, String status, String dtCat, String dtSub) {
        equipmentDataService.save(new EquipmentMessage("CNC02-IT-" + collectedAt, "CNC02", OffsetDateTime.parse(collectedAt),
                cycleCount, status, new BigDecimal("1.2"), new BigDecimal("35.0"), dtCat, dtSub));
    }

    @Test
    @DisplayName("LOT 추적 (재귀 CTE, UPDLOCK): 환봉 입고 → 선삭 → 밀링 → 열처리 Batch, 역방향·정방향 추적, 취소한 실적은 빠진다")
    void lotTrace() throws Exception {
        mockMvc.perform(post("/api/lots/receive").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\":\"RM-OIL-001\",\"qty\":20,\"workerId\":\"W1001\"}"))
                .andExpect(status().isBadRequest());   // 절삭유는 LOT 관리 품목이 아니다
        String barA = receiveBar(10);
        String barB = receiveBar(10);

        String created = mockMvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\":\"FG-SFT-100\",\"planQty\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int op10 = JsonPath.read(created, "$.operations[0].woOpId");
        int op20 = JsonPath.read(created, "$.operations[1].woOpId");
        int op30 = JsonPath.read(created, "$.operations[2].woOpId");

        startOperation(op10);
        String t1 = reportLot(op10, 5, 0, barA, 5);
        String t2 = reportLot(op10, 4, 1, barA, 3, barB, 2);

        mockMvc.perform(get("/api/operations/{id}/input-lots", op20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemCode").value("SF-SFT-010"))
                .andExpect(jsonPath("$[0].fromProcess").value("OP-10"))
                .andExpect(jsonPath("$[0].lots[0].lotNo").value(t1))
                .andExpect(jsonPath("$[0].lots[1].lotNo").value(t2));

        startOperation(op20);
        String m1 = reportLot(op20, 5, 0, t1, 5);
        String m2 = reportLot(op20, 4, 0, t2, 4);
        startOperation(op30);
        String batch = reportLot(op30, 9, 0, m1, 5, m2, 4);

        // 역방향: 열처리 LOT ← 밀링 2 ← 선삭 2 ← 환봉 (barA는 두 경로로 나온다)
        mockMvc.perform(get("/api/lots/{lotNo}/trace", batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lot.itemCode").value("SF-SFT-030"))
                .andExpect(jsonPath("$.backward.length()").value(7))
                .andExpect(jsonPath("$.backward[0].lotNo").value(m1))
                .andExpect(jsonPath("$.backward[0].traceLevel").value(1))
                .andExpect(jsonPath("$.backward[0].processName").value("CNC 밀링"))
                .andExpect(jsonPath("$.backward[1].lotNo").value(t1))
                .andExpect(jsonPath("$.backward[2].lotNo").value(barA))
                .andExpect(jsonPath("$.backward[2].traceLevel").value(3))
                .andExpect(jsonPath("$.backward[2].itemName").value("SCM440 환봉"))
                .andExpect(jsonPath("$.backward[?(@.lotNo == '" + barB + "')].linkQty").value(hasItem(2.0)))
                .andExpect(jsonPath("$.forward.length()").value(0));

        // 실적 하나를 더 하고 취소: 계보 줄은 남지만 추적에서는 빠진다
        String extra = mockMvc.perform(post("/api/operations/{id}/results", op10).contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson(1, 0, barB, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int extraId = JsonPath.read(extra, "$.resultId");
        mockMvc.perform(post("/api/results/{id}/cancel", extraId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\",\"reason\":\"수량 오입력\"}"))
                .andExpect(status().isOk());

        // 정방향: 환봉 barB → 선삭 t2 → 밀링 m2 → 열처리 batch
        mockMvc.perform(get("/api/lots/{lotNo}/trace", barB))
                .andExpect(jsonPath("$.backward.length()").value(0))
                .andExpect(jsonPath("$.forward.length()").value(3))
                .andExpect(jsonPath("$.forward[0].lotNo").value(t2))
                .andExpect(jsonPath("$.forward[1].lotNo").value(m2))
                .andExpect(jsonPath("$.forward[2].lotNo").value(batch))
                .andExpect(jsonPath("$.forward[2].status").value("ACTIVE"));

        // 다음 공정에 쓴 LOT의 실적은 취소할 수 없다
        String results = mockMvc.perform(get("/api/operations/{id}/results", op20))
                .andExpect(jsonPath("$[0].outputLotNos[0]").value(m2))
                .andExpect(jsonPath("$[0].inputs[0].lotNo").value(t2))
                .andReturn().getResponse().getContentAsString();
        int m2Result = JsonPath.read(results, "$[0].resultId");
        mockMvc.perform(post("/api/results/{id}/cancel", m2Result).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\",\"reason\":\"수량 오입력\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOT_IN_USE"));

        mockMvc.perform(get("/api/lots/{lotNo}", batch))
                .andExpect(jsonPath("$.lastProcessName").value("열처리"))
                .andExpect(jsonPath("$.history[0].eventType").value("PRODUCE"))
                .andExpect(jsonPath("$.history[0].processName").value("열처리"));
        mockMvc.perform(get("/lots/trace").param("lotNo", batch))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lots/{lotNo}", barB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lot.qty").value(8.0))
                .andExpect(jsonPath("$.history[*].eventType").value(contains("RECEIVE", "CONSUME", "CONSUME", "CANCEL")));
    }

    private String receiveBar(int qty) throws Exception {
        String body = mockMvc.perform(post("/api/lots/receive").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\":\"RM-SCM-001\",\"qty\":" + qty + ",\"supplierLotNo\":\"SUP-IT\",\"workerId\":\"W1001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.lotNo");
    }

    private void startOperation(int woOpId) throws Exception {
        mockMvc.perform(post("/api/operations/{id}/start", woOpId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"W1001\"}"))
                .andExpect(status().isOk());
    }

    /** 실적을 등록하고 만든 LOT 번호를 돌려준다. lotsAndQty: LOT 번호, 수량, LOT 번호, 수량 ... */
    private String reportLot(int woOpId, int good, int defect, Object... lotsAndQty) throws Exception {
        String body = mockMvc.perform(post("/api/operations/{id}/results", woOpId).contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson(good, defect, lotsAndQty)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.outputLotNos[0]");
    }

    private static String reportJson(int good, int defect, Object... lotsAndQty) {
        StringBuilder inputs = new StringBuilder();
        for (int i = 0; i < lotsAndQty.length; i += 2) {
            inputs.append(i == 0 ? "" : ",").append("{\"lotNo\":\"%s\",\"qty\":%s}".formatted(lotsAndQty[i], lotsAndQty[i + 1]));
        }
        String defects = defect == 0 ? "" : "{\"defectType\":\"OVERSIZE\",\"qty\":%d}".formatted(defect);
        return "{\"requestId\":\"%s\",\"workerId\":\"W1001\",\"goodQty\":%d,\"defects\":[%s],\"inputs\":[%s]}"
                .formatted(UUID.randomUUID(), good, defects, inputs);
    }
}
