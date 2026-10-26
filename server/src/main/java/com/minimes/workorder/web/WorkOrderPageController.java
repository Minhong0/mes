package com.minimes.workorder.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 작업지시 화면. 템플릿은 빈 틀만 그리고, 화면의 JavaScript가 /api/work-orders를 불러 채운다. ?status=로 상태를 고른다. */
@Controller
public class WorkOrderPageController {

    @GetMapping("/work-orders")
    public String workOrders() {
        return "work-orders";   // templates/work-orders.html
    }
}
