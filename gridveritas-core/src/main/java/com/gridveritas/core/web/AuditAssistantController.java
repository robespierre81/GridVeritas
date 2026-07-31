package com.gridveritas.core.web;

import com.gridveritas.core.service.AuditAssistantService;
import com.gridveritas.core.web.dto.AuditAnswerResponse;
import com.gridveritas.core.web.dto.AuditQuestionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuditAssistantController {

    private final AuditAssistantService auditAssistantService;

    public AuditAssistantController(AuditAssistantService auditAssistantService) {
        this.auditAssistantService = auditAssistantService;
    }

    /** Ask a natural-language question about the current audit/attestation state. */
    @PostMapping("/audit/ask")
    public AuditAnswerResponse ask(@Valid @RequestBody AuditQuestionRequest request) {
        return auditAssistantService.ask(request.getQuestion());
    }
}
