package com.minimes.master.domain;

import java.io.Serializable;

/** CommonCode의 복합키 (group_code, code). */
public record CommonCodeId(String groupCode, String code) implements Serializable {

    public CommonCodeId() {
        this(null, null);
    }
}
