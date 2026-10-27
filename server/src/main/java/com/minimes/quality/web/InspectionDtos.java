package com.minimes.quality.web;

import com.minimes.lot.domain.Lot;
import com.minimes.master.domain.InspectionSpec;
import com.minimes.quality.domain.InspectionResult;
import com.minimes.quality.service.InspectionService.InspectCommand;
import com.minimes.quality.service.InspectionService.InspectionSheet;
import com.minimes.quality.service.InspectionService.Measurement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 검사 API의 요청·응답 형식. */
public final class InspectionDtos {

    private InspectionDtos() {
    }

    public record InspectRequest(
            @NotEmpty(message = "측정값이 없습니다.") List<@Valid MeasurementRequest> measurements
    ) {
        public InspectCommand toCommand(String workerId) {
            return new InspectCommand(workerId, measurements.stream()
                    .map(m -> new Measurement(m.inspectCode(), m.sampleNo(), m.value())).toList());
        }
    }

    public record MeasurementRequest(
            @NotBlank(message = "검사 항목은 필수입니다.") String inspectCode,
            @NotNull(message = "시료 번호는 필수입니다.") Integer sampleNo,
            @NotNull(message = "측정값은 필수입니다.") BigDecimal value
    ) {
    }

    /**
     * 검사표. judgement: 검사 전이면 null, 측정값 하나라도 NG면 NG.
     * heldLotNos: 이번 검사가 불합격이라 보류한 LOT (이 LOT과 하위 LOT). 합격이거나 조회면 빈 목록
     */
    public record InspectionResponse(String lotNo, String itemCode, String status, String processCode, String processName,
                                     String judgement, int sampleCount, String workerId, LocalDateTime measuredAt,
                                     List<ItemResponse> items, List<String> heldLotNos) {
        public static InspectionResponse from(InspectionSheet s) {
            Lot lot = s.lot();
            Map<String, List<InspectionResult>> byCode = s.results().stream()
                    .collect(Collectors.groupingBy(InspectionResult::getInspectCode));
            InspectionResult first = s.results().isEmpty() ? null : s.results().getFirst();
            return new InspectionResponse(lot.getLotNo(), lot.getItemCode(), lot.getStatus().name(), lot.getLastProcess(),
                    s.processName(), s.judgement() == null ? null : s.judgement().name(), s.sampleCount(),
                    first == null ? null : first.getWorkerId(), first == null ? null : first.getMeasuredAt(),
                    s.specs().stream().map(spec -> ItemResponse.from(spec, byCode.getOrDefault(spec.getInspectCode(), List.of()))).toList(),
                    s.hold() == null ? List.of() : s.hold().changed().stream().map(Lot::getLotNo).toList());
        }
    }

    /** 검사 항목 하나와 시료별 측정값 (시료 번호 순서) */
    public record ItemResponse(String inspectCode, String inspectName, String unit, BigDecimal nominal, BigDecimal lsl,
                               BigDecimal usl, String equipCode, List<SampleResponse> samples) {
        static ItemResponse from(InspectionSpec spec, List<InspectionResult> results) {
            return new ItemResponse(spec.getInspectCode(), spec.getInspectName(), spec.getUnit(), spec.getNominal(),
                    spec.getLsl(), spec.getUsl(), spec.getEquipCode(),
                    results.stream().map(r -> new SampleResponse(r.getSampleNo(), r.getMeasuredValue(), r.getJudgement().name())).toList());
        }
    }

    public record SampleResponse(int sampleNo, BigDecimal value, String judgement) {
    }
}
