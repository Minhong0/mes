package com.minimes.equipment.repository;

import java.time.LocalDateTime;

/** 측정 시각 범위. JPQL의 select new로 바로 만든다 (EquipmentDataRepository.findCollectedRangeReceivedSince). */
public record CollectedRange(LocalDateTime from, LocalDateTime to) {

    public boolean isEmpty() {
        return from == null;
    }
}
