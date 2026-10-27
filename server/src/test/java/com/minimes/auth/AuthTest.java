package com.minimes.auth;

import com.jayway.jsonpath.JsonPath;
import com.minimes.support.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인과 권한: 사번·비밀번호로 토큰을 받고, 토큰(헤더 또는 쿠키) 없이는 API와 화면을 쓸 수 없다.
 * 누가 했는지는 요청 본문이 아니라 토큰의 사번으로 남는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql", "/sql/lot-fixture.sql", "/sql/auth-fixture.sql"})
class AuthTest {

    @Autowired
    MockMvc mockMvc;

    MvcResult login(String workerId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\": \"%s\", \"password\": \"%s\"}".formatted(workerId, password)))
                .andReturn();
    }

    @Test
    @DisplayName("로그인: 토큰과 작업자 정보, 같은 토큰을 HttpOnly·SameSite=Strict 쿠키로도 준다. 토큰은 헤더로도 쿠키로도 통한다")
    void loginAndUseToken() throws Exception {
        MvcResult result = login("W1001", "1234");
        String body = result.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\": \" W1001 \", \"password\": \"1234\"}"))   // 바코드 앞뒤 공백
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.worker.workerName").value("김도윤"))
                .andExpect(jsonPath("$.worker.role").value("OPERATOR"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("MES_TOKEN="), containsString("HttpOnly"), containsString("SameSite=Strict"))));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerId").value("W1001"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("MES_TOKEN", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerName").value("김도윤"));
    }

    @Test
    @DisplayName("틀린 비밀번호, 없는 사번, 퇴사자는 모두 같은 401 LOGIN_FAILED (어느 사번이 있는지 알려 주지 않는다)")
    void loginFailed() throws Exception {
        for (String[] attempt : new String[][]{{"W1001", "0000"}, {"W0000", "1234"}, {"W1099", "1234"}}) {
            MvcResult result = login(attempt[0], attempt[1]);
            org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(401);
            org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                    .contains("LOGIN_FAILED", "사번 또는 비밀번호가 맞지 않습니다.");
        }
    }

    @Test
    @DisplayName("토큰 없이: API는 401 UNAUTHORIZED JSON, 화면은 로그인 화면으로 (돌아올 주소를 next에). 로그인 화면은 열린다")
    void withoutToken() throws Exception {
        mockMvc.perform(get("/api/work-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/lots/trace?lotNo=A-1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?next=%2Flots%2Ftrace%3FlotNo%3DA-1"));
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("위조·만료 토큰: 401 '로그인이 만료되었습니다', 쿠키를 지운다. 그 쿠키가 남아 있어도 로그인 화면은 열린다")
    void invalidToken() throws Exception {
        Cookie forged = new Cookie("MES_TOKEN", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJXOTAwMSJ9.forged");
        mockMvc.perform(get("/api/tools").cookie(forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 만료되었습니다. 다시 로그인하세요."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mockMvc.perform(get("/tools").cookie(forged))
                .andExpect(redirectedUrl("/login?next=%2Ftools&expired"));
        mockMvc.perform(get("/login").cookie(forged))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("역할: 작업자는 작업지시를 만들거나 LOT을 보류할 수 없다 (403 NOT_ALLOWED). 품질 담당은 보류할 수 있다")
    void roles() throws Exception {
        mockMvc.perform(post("/api/work-orders").with(TestLogin.as("W1001", "OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemCode\": \"FG-SFT-100\", \"planQty\": 10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED"));
        mockMvc.perform(post("/api/lots/{lotNo}/hold", "RM-SCM-001-261017-001").with(TestLogin.as("W1001", "OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"OD check\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/lots/{lotNo}/hold", "NO-SUCH-LOT").with(TestLogin.as("W6001", "QC"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"OD check\"}"))
                .andExpect(status().isNotFound());   // 권한은 통과하고 서비스까지 갔다
    }

    @Test
    @DisplayName("누가 했는지는 토큰의 사번: 본문에 다른 사번을 넣어도 무시한다")
    void workerFromToken() throws Exception {
        mockMvc.perform(post("/api/lots/receive").with(TestLogin.as("W1001", "OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemCode\": \"RM-SCM-001\", \"qty\": 10, \"workerId\": \"W6001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("W1001"));
    }
}
