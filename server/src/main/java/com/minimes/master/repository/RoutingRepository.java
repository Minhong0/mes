package com.minimes.master.repository;

import com.minimes.master.domain.Routing;
import com.minimes.master.domain.RoutingId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutingRepository extends JpaRepository<Routing, RoutingId> {

    List<Routing> findByItemCodeOrderBySeq(String itemCode);
}
