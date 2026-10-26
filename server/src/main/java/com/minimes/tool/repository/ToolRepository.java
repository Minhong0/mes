package com.minimes.tool.repository;

import com.minimes.tool.domain.Tool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolRepository extends JpaRepository<Tool, String> {

    List<Tool> findAllByOrderByEquipCodeAscToolCodeAsc();
}
