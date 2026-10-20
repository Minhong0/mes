package com.minimes.monitoring.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 대시보드 화면. 템플릿은 빈 틀만 그리고, 데이터는 화면의 JavaScript가 /api/monitoring/dashboard에서 받아 채운다. */
@Controller
public class MonitoringPageController {

    @GetMapping("/monitoring")
    public String monitoring() {
        return "monitoring";   // templates/monitoring.html
    }

    @GetMapping("/monitoring/pareto")
    public String pareto() {
        return "pareto";       // templates/pareto.html
    }
}
