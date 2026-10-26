package com.minimes.tool.domain;

/** 공구 종류. tool.tool_type의 CHECK 제약과 같은 값. */
public enum ToolType {
    INSERT,    // 선삭 인서트
    ENDMILL,   // 밀링 엔드밀
    DRILL,     // 드릴
    WHEEL      // 연삭석
}
