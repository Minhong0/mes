package com.minimes.quality.repository;

import com.minimes.quality.domain.InspectionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {

    List<InspectionResult> findByLotNoAndProcessCodeOrderByInspectCodeAscSampleNoAsc(String lotNo, String processCode);
}
