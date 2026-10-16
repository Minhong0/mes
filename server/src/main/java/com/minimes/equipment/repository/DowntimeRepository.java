package com.minimes.equipment.repository;

import com.minimes.equipment.domain.Downtime;
import com.minimes.equipment.domain.DowntimeSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DowntimeRepository extends JpaRepository<Downtime, Long> {

    /** 아직 끝나지 않은 정지 (설비가 자동으로 남긴 것만) */
    Optional<Downtime> findFirstByEquipCodeAndSourceAndEndedAtIsNullOrderByStartedAtDesc(String equipCode, DowntimeSource source);

    List<Downtime> findByEquipCodeAndSourceOrderByStartedAt(String equipCode, DowntimeSource source);
}
