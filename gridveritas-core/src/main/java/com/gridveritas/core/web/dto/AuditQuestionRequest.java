package com.gridveritas.core.web.dto;

import jakarta.validation.constraints.NotBlank;

public class AuditQuestionRequest {

    @NotBlank
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
