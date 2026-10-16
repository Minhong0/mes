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
}
