package com.minimes.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 관리자 화면들: 틀(템플릿)이 뜨고, 맨 위 메뉴(fragments/nav.html)에서 지금 화면이 표시되고, JS가 쓸 API 주소가 data-*로 넘어간다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageNavigationTest {

    @Autowired
    MockMvc mockMvc;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/monitoring,        monitoring,  설비 현황,       data-api-url=\"/api/monitoring/dashboard\"",
            "/monitoring/pareto, pareto,      정지 사유 파레토, data-api-url=\"/api/monitoring/downtime-pareto\"",
            "/work-orders,       work-orders, 작업지시,        data-wo-url=\"/api/work-orders\"",
            "/lots/trace,        lot-trace,   LOT 추적,       data-lots-url=\"/api/lots\"",
            "/lots/hold,         lot-hold,    보류 LOT,       data-held-url=\"/api/lots/held\"",
    })
    @DisplayName("화면마다 메뉴가 있고 지금 화면이 active다")
    void navigation(String path, String viewName, String menu, String dataAttr) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().string(allOf(
                        containsString("class=\"nav-bar\""),
                        containsString("aria-current=\"page\" class=\"active\">" + menu + "</a>"),
                        containsString(dataAttr))));
    }
}
