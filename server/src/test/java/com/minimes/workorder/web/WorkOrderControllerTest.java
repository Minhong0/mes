package com.minimes.workorder.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 요청부터 응답 JSON까지 확인한다 (MockMvc).
 * 특히 오류 응답이 ErrorResponse 형식(code, message, fieldErrors)으로 나오는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/sql/work-order-fixture.sql")
class WorkOrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/work-orders → 201, 공정 3개가 함께 생성된다")
    void create() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode": "FG-SFT-100", "planQty": 100, "dueDate": "2026-10-10"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAIT"))
                .andExpect(jsonPath("$.operations.length()").value(3))
                .andExpect(jsonPath("$.operations[2].processCode").value("OP-30"));
    }

    @Test
    @Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql"})
    @DisplayName("공정마다 공정 이름이 붙는다 (관리자 화면이 OP-10 대신 'CNC 선삭'을 보여 준다). 목록도 같다")
    void processNames() throws Exception {
        mockMvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\": \"FG-SFT-100\", \"planQty\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operations[0].processName").value("CNC 선삭"))
                .andExpect(jsonPath("$.operations[2].processName").value("열처리"));
        mockMvc.perform(get("/api/work-orders"))
                .andExpect(jsonPath("$[0].operations[1].processName").value("CNC 밀링"));
    }

    @Test
    @DisplayName("계획수량 0 → 400 INVALID_INPUT, 어느 필드가 틀렸는지 알려준다")
    void validationError() throws Exception {
        mockMvc.perform(post("/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode": "FG-SFT-100", "planQty": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("planQty"));
    }

    @Test
    @DisplayName("대기 상태에서 마감 → 409 INVALID_STATUS_TRANSITION")
    void invalidTransition() throws Exception {
        String body = mockMvc.perform(post("/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemCode": "FG-SFT-100", "planQty": 10}
                                """))
                .andReturn().getResponse().getContentAsString();
        String woNo = com.jayway.jsonpath.JsonPath.read(body, "$.woNo");

        mockMvc.perform(post("/api/work-orders/{woNo}/close", woNo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("없는 작업지시 → 404 WORK_ORDER_NOT_FOUND")
    void notFound() throws Exception {
        mockMvc.perform(get("/api/work-orders/{woNo}", "WO-000000-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_ORDER_NOT_FOUND"));
    }
}
