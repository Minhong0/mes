package com.minimes.lot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * LOT 추적·보류 목록 화면. 템플릿은 빈 틀만 그리고, 화면의 JavaScript가 /api/lots/...를 불러 채운다.
 * 주소에 ?lotNo=를 붙이면 그 LOT을 바로 추적한다.
 */
@Controller
public class LotPageController {

    @GetMapping("/lots/trace")
    public String trace() {
        return "lot-trace";   // templates/lot-trace.html
    }

    /** 보류 중인 LOT 목록. 데이터는 /api/lots/held */
    @GetMapping("/lots/hold")
    public String hold() {
        return "lot-hold";    // templates/lot-hold.html
    }
}
