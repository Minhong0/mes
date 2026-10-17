package com.minimes.master.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.master.domain.CommonCode;
import com.minimes.master.domain.Equipment;
import com.minimes.master.domain.Item;
import com.minimes.master.domain.ItemType;
import com.minimes.master.domain.ManufacturingProcess;
import com.minimes.master.domain.Routing;
import com.minimes.master.domain.Worker;
import com.minimes.master.mapper.BomMapper;
import com.minimes.master.mapper.BomNode;
import com.minimes.master.repository.CommonCodeRepository;
import com.minimes.master.repository.EquipmentRepository;
import com.minimes.master.repository.ItemRepository;
import com.minimes.master.repository.ProcessRepository;
import com.minimes.master.repository.RoutingRepository;
import com.minimes.master.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 기준정보 조회. 조회만 하므로 readOnly 트랜잭션을 쓴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterQueryService {

    private final ItemRepository itemRepository;
    private final RoutingRepository routingRepository;
    private final ProcessRepository processRepository;
    private final EquipmentRepository equipmentRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final WorkerRepository workerRepository;
    private final BomMapper bomMapper;

    public List<Item> findItems(ItemType type) {
        return type == null
                ? itemRepository.findAllByOrderByItemCode()
                : itemRepository.findByItemTypeOrderByItemCode(type);
    }

    public Item getItem(String itemCode) {
        return itemRepository.findById(itemCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "품목을 찾을 수 없습니다: " + itemCode));
    }

    public List<Routing> findRoutings(String itemCode) {
        getItem(itemCode);   // 없는 품목이면 404
        return routingRepository.findByItemCodeOrderBySeq(itemCode);
    }

    /** 공정 코드 → 공정. 라우팅 응답에 공정 이름을 붙일 때 쓴다. 공정은 7개뿐이라 한 번에 읽는다. */
    public Map<String, ManufacturingProcess> processMap() {
        return processRepository.findAll().stream()
                .collect(Collectors.toMap(ManufacturingProcess::getProcessCode, Function.identity()));
    }

    public List<BomNode> findBomTree(String itemCode) {
        getItem(itemCode);
        return bomMapper.selectBomTree(itemCode);
    }

    public List<Equipment> findEquipment() {
        return equipmentRepository.findAllByOrderByEquipCode();
    }

    public List<CommonCode> findCodes(String groupCode) {
        return commonCodeRepository.findByGroupCodeOrderBySortOrder(groupCode);
    }

    /** 사용 중인 작업자. 없거나 퇴사(use_yn = N)면 404 */
    public Worker getActiveWorker(String workerId) {
        return workerRepository.findById(workerId)
                .filter(Worker::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKER_NOT_FOUND, "작업자를 찾을 수 없습니다: " + workerId));
    }
}
