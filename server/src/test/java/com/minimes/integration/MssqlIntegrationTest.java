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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$[2].timeBasis").value("BATCH"));
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
}
