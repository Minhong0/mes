package com.minimes.master.domain;

import java.io.Serializable;

/** Routing의 복합키 (item_code, seq). JPA가 equals/hashCode로 같은 행인지 판단하므로 record로 만든다. */
public record RoutingId(String itemCode, Integer seq) implements Serializable {

    // JPA가 리플렉션으로 생성할 수 있도록 기본 생성자를 둔다.
    public RoutingId() {
        this(null, null);
    }
}
