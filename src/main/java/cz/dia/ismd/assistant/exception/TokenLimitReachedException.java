package cz.dia.ismd.assistant.exception;

import java.util.UUID;

public class TokenLimitReachedException extends RuntimeException{
    public TokenLimitReachedException(UUID jobId, String userId) {
        super("Cannot run job: " + jobId + " for user: " + userId + " because that user's daily token limit has been reached.");
    }
}
