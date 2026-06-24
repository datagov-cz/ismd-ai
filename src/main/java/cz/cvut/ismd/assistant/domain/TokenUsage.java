package cz.cvut.ismd.assistant.domain;

public record TokenUsage (
    String user_id,
    Integer usedTokens
) {

}
