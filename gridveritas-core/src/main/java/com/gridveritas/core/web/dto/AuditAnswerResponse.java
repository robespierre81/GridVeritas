package com.gridveritas.core.web.dto;

public class AuditAnswerResponse {

    private boolean answered;   // true if the model produced an answer
    private String answer;
    private String model;
    private String note;        // set when the assistant is unavailable/disabled
    private String contextUsed; // the retrieved facts the answer was grounded on (transparency)

    public boolean isAnswered() {
        return answered;
    }

    public void setAnswered(boolean answered) {
        this.answered = answered;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getContextUsed() {
        return contextUsed;
    }

    public void setContextUsed(String contextUsed) {
        this.contextUsed = contextUsed;
    }
}
