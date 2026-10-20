package com.minimes.lot.service;

import com.minimes.lot.domain.Lot;
import com.minimes.lot.domain.LotGenealogy;
import com.minimes.lot.domain.LotHistory;
import com.minimes.lot.domain.LotStatus;
import com.minimes.lot.repository.LotGenealogyRepository;
import com.minimes.lot.repository.LotRepository;
import com.minimes.lot.service.LotService.ReceiveCommand;
import com.minimes.workorder.domain.WorkResult;
import com.minimes.workorder.domain.WorkResult.DefectLine;
import com.minimes.workorder.service.OperationService;
import com.minimes.workorder.service.OperationService.ReportCommand;
import com.minimes.workorder.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** 포장 분할: LOT 크기(routing.lot_size = 20)가 정해진 공정은 양품을 20개씩 여러 LOT으로 나눈다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql", "/sql/pack-fixture.sql"})
class LotPackTest {

    @Autowired
    LotService lotService;

    @Autowired
    OperationService operationService;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    LotRepository lotRepository;

    @Autowired
    LotGenealogyRepository genealogyRepository;

    long op70;

    @BeforeEach
    void createWorkOrder() {
        op70 = workOrderService.create("FG-PACK", 100, null, null).getOperations().getFirst().getWoOpId();
        operationService.start(op70, "W1001");
    }

    Lot receive(int qty) {
        return lotService.receive(new ReceiveCommand("RM-BAR", BigDecimal.valueOf(qty), null, "W1001"));
    }

    WorkResult report(int good, int defect, LotInput... inputs) {
        List<DefectLine> defects = defect == 0 ? List.of() : List.of(new DefectLine("SCRATCH", defect));
        return operationService.report(op70,
                new ReportCommand(UUID.randomUUID().toString(), "W1001", null, good, defects, List.of(inputs)));
    }

    List<String> outputsOf(WorkResult result) {
        return lotService.resultLots(List.of(result.getResultId())).get(result.getResultId()).outputLotNos();
    }

    static LotInput in(Lot lot, int qty) {
        return new LotInput(lot.getLotNo(), BigDecimal.valueOf(qty));
    }

    @Test
    @DisplayName("LOT 크기로 나누기: 45개 → 20, 20, 5 / 20개 → 나누지 않음 / 크기가 없으면 그대로 / 0개 → 없음")
    void splitSizes() {
        assertThat(LotService.splitSizes(45, 20)).containsExactly(20, 20, 5);
        assertThat(LotService.splitSizes(40, 20)).containsExactly(20, 20);
        assertThat(LotService.splitSizes(20, 20)).containsExactly(20);
        assertThat(LotService.splitSizes(45, null)).containsExactly(45);
        assertThat(LotService.splitSizes(0, 20)).isEmpty();
    }

    @Test
    @DisplayName("양품 45개 → 트레이 LOT 3개(20, 20, 5). 이력에 '분할 1/3'이 남는다")
    void splitIntoTrays() {
        Lot bar = receive(50);

        WorkResult result = report(45, 0, in(bar, 45));

        List<String> trays = outputsOf(result);
        assertThat(trays).hasSize(3);
        assertThat(trays).extracting(no -> lotRepository.findById(no).orElseThrow().getQty().intValue())
                .containsExactly(20, 20, 5);
        assertThat(trays).allSatisfy(no -> assertThat(lotRepository.findById(no).orElseThrow().getStatus())
                .isEqualTo(LotStatus.ACTIVE));
        assertThat(lotService.detail(trays.getFirst()).history()).extracting(LotHistory::getNote)
                .containsExactly("분할 1/3");
    }

    @Test
    @DisplayName("계보: 투입 LOT을 적은 순서대로 트레이에 채운다. 불량 몫은 마지막 트레이에 붙어 LOT마다 합이 투입 수량과 같다")
    void genealogyFillsInOrder() {
        Lot a = receive(25);
        Lot b = receive(30);

        WorkResult result = report(42, 3, in(a, 25), in(b, 20));   // 45개 투입, 양품 42 → 20, 20, 2

        List<String> trays = outputsOf(result);
        assertThat(genealogyRepository.findByResultId(result.getResultId()))
                .extracting(LotGenealogy::getInputLotNo, LotGenealogy::getOutputLotNo, g -> g.getQty().intValue())
                .containsExactly(
                        tuple(a.getLotNo(), trays.get(0), 20),
                        tuple(a.getLotNo(), trays.get(1), 5),
                        tuple(b.getLotNo(), trays.get(1), 15),
                        tuple(b.getLotNo(), trays.get(2), 5));   // 양품 2 + 불량 3
    }

    @Test
    @DisplayName("실적 취소: 트레이 LOT 셋을 모두 폐기하고 투입 수량을 돌려준다")
    void cancelScrapsAllTrays() {
        Lot bar = receive(50);
        WorkResult result = report(45, 0, in(bar, 45));

        operationService.cancel(result.getResultId(), "W1001", "수량 오입력");

        assertThat(outputsOf(result)).allSatisfy(no -> assertThat(lotRepository.findById(no).orElseThrow().getStatus())
                .isEqualTo(LotStatus.SCRAPPED));
        assertThat(lotRepository.findById(bar.getLotNo()).orElseThrow().getQty()).isEqualByComparingTo("50");
    }
}
