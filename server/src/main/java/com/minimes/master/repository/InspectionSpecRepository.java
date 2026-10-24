package com.minimes.master.repository;

import com.minimes.master.domain.InspectionSpec;
import com.minimes.master.domain.InspectionSpecId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionSpecRepository extends JpaRepository<InspectionSpec, InspectionSpecId> {

    List<InspectionSpec> findByProcessCodeOrderByInspectCode(String processCode);
}
