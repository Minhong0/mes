package com.minimes.tool.repository;

import com.minimes.tool.domain.ToolChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolChangeRepository extends JpaRepository<ToolChange, Long> {

    List<ToolChange> findTop50ByOrderByChangeIdDesc();

    List<ToolChange> findTop50ByToolCodeOrderByChangeIdDesc(String toolCode);
}
