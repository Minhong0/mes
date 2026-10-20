package com.minimes.lot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * LOT 추적 화면. 템플릿은 빈 틀만 그리고, 화면의 JavaScript가 /api/lots/...를 불러 채운다.
 * 주소에 ?lotNo=를 붙이면 그 LOT을 바로 추적한다.
 */
@Controller
public class LotPageController {

    @GetMapping("/lots/trace")
    public String trace() {
        return "lot-trace";   // templates/lot-trace.html
    }
}
