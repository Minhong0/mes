package com.minimes.production.repository;

import com.minimes.production.domain.ProductionDaily;
import com.minimes.production.domain.ProductionDailyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionDailyRepository extends JpaRepository<ProductionDaily, ProductionDailyId> {
}
