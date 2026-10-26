package com.minimes.equipment.service;

import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.repository.EquipmentDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 설비가 가공한 수량. 메시지의 누적 카운트(cycleCount)가 늘어난 양을 더한다.
 * 일별 생산 실적과 공구 사용 수량이 같은 규칙으로 센다.
 *
 *   구간 앞 마지막 30 → 31, 32, 33 → 0, 1, 2   : 1 + 1 + 1 + 0 + 1 + 1 = 5개
 *                                  ↑ 시뮬레이터 재시작 (카운트가 0부터 다시)
 *
 * 카운트가 줄었으면 재시작한 것이므로, 새 카운트 값(재시작 후 만든 개수)을 그대로 더한다.
 * 늘 측정 시각 순서로 다시 세므로, 메시지가 늦게 도착해도 결과가 같다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProducedCounter {

    private final EquipmentDataRepository equipmentDataRepository;

    /** [from, to) 동안 가공한 수량 (일별 생산 실적) */
    public int count(String equipCode, LocalDateTime from, LocalDateTime to) {
        Integer previous = equipmentDataRepository.findFirstByEquipCodeAndCollectedAtLessThanOrderByCollectedAtDesc(equipCode, from)
                .map(EquipmentData::getCycleCount)
                .orElse(null);
        return sum(previous, equipmentDataRepository
                .findByEquipCodeAndCollectedAtGreaterThanEqualAndCollectedAtLessThanOrderByCollectedAt(equipCode, from, to));
    }

    /**
     * after보다 뒤에 가공한 수량, 지금까지 (공구 사용 수량).
     * after 시각의 메시지는 기준으로만 쓴다: 그 메시지의 카운트는 공구를 달기 전에 이미 올라간 것이다.
     */
    public int countAfter(String equipCode, LocalDateTime after) {
        Integer previous = equipmentDataRepository.findFirstByEquipCodeAndCollectedAtLessThanEqualOrderByCollectedAtDesc(equipCode, after)
                .map(EquipmentData::getCycleCount)
                .orElse(null);
        return sum(previous, equipmentDataRepository.findByEquipCodeAndCollectedAtGreaterThanOrderByCollectedAt(equipCode, after));
    }

    private static int sum(Integer previous, List<EquipmentData> messages) {
        int total = 0;
        for (EquipmentData data : messages) {
            Integer current = data.getCycleCount();
            if (current == null) {
                continue;
            }
            if (previous != null) {
                total += current >= previous ? current - previous : current;
            }
            previous = current;
        }
        return total;
    }
}
