package cz.dia.ismd.assistant.exception;

import java.util.UUID;

public class SuggestionNotFoundException extends RuntimeException {

    public SuggestionNotFoundException(UUID jobId, String suggestionId) {
        super("Suggestion not found for job " + jobId + ": " + suggestionId);
    }
}
