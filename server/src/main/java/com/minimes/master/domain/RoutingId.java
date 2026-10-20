package com.minimes.master.domain;

import java.io.Serializable;

/** Routing의 복합키 (item_code, seq). */
public record RoutingId(String itemCode, Integer seq) implements Serializable {

    // JPA가 리플렉션으로 생성할 수 있도록 기본 생성자를 둔다.
    public RoutingId() {
        this(null, null);
    }
}
