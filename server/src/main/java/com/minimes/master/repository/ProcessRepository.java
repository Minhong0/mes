package com.minimes.master.repository;

import com.minimes.master.domain.ManufacturingProcess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessRepository extends JpaRepository<ManufacturingProcess, String> {
}
