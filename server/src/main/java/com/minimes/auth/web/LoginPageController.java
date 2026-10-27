package com.minimes.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 관리자 웹 로그인 화면. 로그인은 화면의 JS가 /api/auth/login을 불러서 하고, 서버가 쿠키를 심는다. */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
