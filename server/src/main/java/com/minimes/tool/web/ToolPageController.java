package com.minimes.tool.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 공구 수명 화면. 템플릿은 빈 틀만 그리고, 화면의 JavaScript가 /api/tools를 불러 채운다. */
@Controller
public class ToolPageController {

    @GetMapping("/tools")
    public String tools() {
        return "tools";   // templates/tools.html
    }
}
