package cz.dia.ismd.assistant.exception;

public class TokenLimitReachedException extends RuntimeException{
    public TokenLimitReachedException(String userId, int tokenLimit) {
        super("Cannot make an LLM request for user: " + userId
                + " because that user's token limit of " + tokenLimit + " has been reached.");
    }
}
