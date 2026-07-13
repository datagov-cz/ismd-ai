package cz.dia.ismd.assistant.model.job;

import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    JobStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
