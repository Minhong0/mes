package com.minimes.master.domain;

import java.io.Serializable;

/** InspectionSpec의 복합키 (process_code, inspect_code). */
public record InspectionSpecId(String processCode, String inspectCode) implements Serializable {

    public InspectionSpecId() {
        this(null, null);
    }
}
