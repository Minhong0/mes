package com.minimes.monitoring.service;

import com.minimes.equipment.service.EquipmentStatusService;
import com.minimes.monitoring.mapper.EquipmentNow;
import com.minimes.monitoring.mapper.MonitoringMapper;
import com.minimes.monitoring.service.Dashboard.EquipmentCard;
import com.minimes.monitoring.service.Dashboard.Stop;
import com.minimes.monitoring.service.Dashboard.TodayOee;
import com.minimes.production.mapper.OeeDaily;
import com.minimes.production.mapper.OeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 설비 현황 대시보드 데이터를 만든다.
 *
 * "지금"을 서버 시계가 아니라 공장 시계(설비가 보낸 가장 늦은 측정 시각)로 정한다.
 * 시뮬레이터를 배속으로 돌리면 공장 시계가 현실보다 앞서 가므로, 서버 시계로 "몇 분째"나 "오늘"을 계산하면 틀린다.
 */
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MonitoringMapper monitoringMapper;
    private final OeeMapper oeeMapper;
    private final Clock clock;

    public Dashboard dashboard() {
        List<EquipmentNow> rows = monitoringMapper.selectEquipmentNow();

        LocalDateTime factoryNow = rows.stream()
                .map(EquipmentNow::lastSeenAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now(clock));   // 아직 데이터가 하나도 없으면 서버 시각
        LocalDate prodDate = factoryNow.toLocalDate();

        // 설비 코드 → 오늘 OEE. 카드를 만들 때 설비마다 찾아 붙인다.
        Map<String, OeeDaily> oeeByEquip = oeeMapper.selectDaily(prodDate).stream()
                .collect(Collectors.toMap(OeeDaily::equipCode, Function.identity()));

        List<EquipmentCard> cards = rows.stream()
                .map(row -> toCard(row, factoryNow, oeeByEquip.get(row.equipCode())))
                .toList();
        return new Dashboard(factoryNow, prodDate, cards);
    }

    private EquipmentCard toCard(EquipmentNow row, LocalDateTime factoryNow, OeeDaily oee) {
        boolean online = row.lastSeenAt() != null
                && Duration.between(row.lastSeenAt(), factoryNow).compareTo(EquipmentStatusService.OFFLINE_GAP) <= 0;
        Long statusMinutes = row.statusSince() == null ? null : Duration.between(row.statusSince(), factoryNow).toMinutes();
        Stop stop = row.stopStartedAt() == null ? null
                : new Stop(row.dtCat(), row.dtCatName(), row.dtSub(), row.dtSubName(), row.stopStartedAt());
        TodayOee today = oee == null ? null
                : new TodayOee(oee.prodQty(), oee.downtimeMin(), oee.availability(), oee.performance(), oee.quality(), oee.oee());

        return new EquipmentCard(row.equipCode(), row.equipName(), online, row.status(), row.statusName(),
                row.statusSince(), statusMinutes, stop, row.cycleCount(), row.spindleVibration(), row.spindleTemp(),
                row.lastSeenAt(), today);
    }
}
