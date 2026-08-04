package cz.dia.ismd.assistant.exception;

public class JobIdsLimitExceededException extends RuntimeException {

    public JobIdsLimitExceededException(int actual, int maximum) {
        super("Too many job IDs requested: " + actual + " provided, maximum is " + maximum);
    }
}
