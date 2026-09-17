package cz.dia.ismd.assistant.exception;

public class VocabularyJobCapacityException extends RuntimeException {
    public VocabularyJobCapacityException() {
        super("Vocabulary generation capacity is exhausted; retry later");
    }
}
