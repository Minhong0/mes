package com.minimes.production.domain;

import java.io.Serializable;
import java.time.LocalDate;

/** ProductionDaily의 복합키 (prod_date, equip_code). RoutingId와 같은 방식이다. */
public record ProductionDailyId(LocalDate prodDate, String equipCode) implements Serializable {

    public ProductionDailyId() {
        this(null, null);
    }
}
