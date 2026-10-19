package com.minimes.lot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * LOT 추적 화면의 틀과, 화면이 쓰는 LOT 상세 API(공정 이름 포함).
 * 추적 트리(재귀 CTE)는 MSSQL 전용 문법이라 MssqlIntegrationTest가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql", "/sql/lot-fixture.sql"})
class LotPageTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET /lots/trace → 화면 틀. API 주소는 data-* 속성으로 JS에 넘긴다")
    void page() throws Exception {
        mockMvc.perform(get("/lots/trace").param("lotNo", "RM-SCM-001-261017-001"))
                .andExpect(status().isOk())
                .andExpect(view().name("lot-trace"))
                .andExpect(content().string(containsString("data-lots-url=\"/api/lots\"")))
                .andExpect(content().string(containsString("id=\"backward\"")))
                .andExpect(content().string(containsString("/js/lot-trace.js")));
    }

    @Test
    @DisplayName("LOT 상세: 이력의 공정 이름과 LOT을 만든 공정 이름이 함께 온다. 없는 LOT은 404")
    void detailWithProcessNames() throws Exception {
        String receive = """
                {"itemCode": "RM-SCM-001", "qty": 10, "workerId": "W1001"}
                """;
        String body = mockMvc.perform(post("/api/lots/receive").contentType(MediaType.APPLICATION_JSON).content(receive))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String lotNo = com.jayway.jsonpath.JsonPath.read(body, "$.lotNo");

        mockMvc.perform(get("/api/lots/{lotNo}", lotNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("SCM440 환봉"))
                .andExpect(jsonPath("$.lastProcessName").doesNotExist())   // 원자재: 만든 공정이 없다
                .andExpect(jsonPath("$.history[0].eventType").value("RECEIVE"))
                .andExpect(jsonPath("$.history[0].processName").doesNotExist());

        mockMvc.perform(get("/api/lots/{lotNo}", "NO-SUCH-LOT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"));
    }
}
