package com.seatflow.qa;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.common.ApiResponse;
import com.seatflow.qa.dto.QaRequest;
import com.seatflow.qa.dto.QaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final KnowledgeIngestionService ingestionService;

    @PostMapping
    public ApiResponse<QaResponse> ask(@Valid @RequestBody QaRequest request) {
        return ApiResponse.success(qaService.answer(request));
    }

    @PostMapping("/reindex")
    public ApiResponse<Integer> reindex() {
        return ApiResponse.success(ingestionService.ingest());
    }
}
