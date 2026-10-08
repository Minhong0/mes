package com.minimes.master.repository;

import com.minimes.master.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, String> {

    List<Equipment> findAllByOrderByEquipCode();
}
