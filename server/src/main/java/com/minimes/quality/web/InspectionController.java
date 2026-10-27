package com.minimes.quality.web;

import com.minimes.auth.config.LoginWorker;
import com.minimes.quality.service.InspectionService;
import com.minimes.quality.web.InspectionDtos.InspectRequest;
import com.minimes.quality.web.InspectionDtos.InspectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검사", description = "LOT 검사표 조회, 시료별 측정값 등록과 합격·불합격 판정 (불합격이면 자동 보류)")
@RestController
@RequestMapping("/api/lots/{lotNo}/inspection")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @Operation(summary = "검사표", description = "LOT을 만든 공정의 검사 항목(하한·상한)과, 이미 검사했으면 시료별 측정값과 판정")
    @GetMapping
    public InspectionResponse sheet(@PathVariable String lotNo) {
        return InspectionResponse.from(inspectionService.sheet(lotNo));
    }

    @Operation(summary = "검사 등록", description = "모든 항목을 시료 1~n번까지. 하나라도 NG면 LOT과 하위 LOT을 보류한다. "
            + "같은 값을 다시 보내면 처음 결과를, 다른 값이면 409 INSPECTION_DONE")
    @PostMapping
    public InspectionResponse inspect(@PathVariable String lotNo, @Valid @RequestBody InspectRequest req,
                                      @LoginWorker String workerId) {
        return InspectionResponse.from(inspectionService.inspect(lotNo, req.toCommand(workerId)));
    }
}
