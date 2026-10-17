package com.minimes.workorder.repository;

import com.minimes.workorder.domain.WorkOrderOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderOperationRepository extends JpaRepository<WorkOrderOperation, Long> {
}
