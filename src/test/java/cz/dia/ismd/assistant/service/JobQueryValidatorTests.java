package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.api.ApiExceptionHandler;
import cz.dia.ismd.assistant.config.JobQueryProperties;
import cz.dia.ismd.assistant.exception.JobIdsLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobQueryValidatorTests {

    @Test
    void usesDefaultLimitOfOneHundred() {
        JobQueryValidator validator = new JobQueryValidator(new JobQueryProperties(null));

        assertDoesNotThrow(() -> validator.validate(Collections.nCopies(100, "job-id")));
        JobIdsLimitExceededException exception = assertThrows(
                JobIdsLimitExceededException.class,
                () -> validator.validate(Collections.nCopies(101, "job-id"))
        );
        assertEquals("Too many job IDs requested: 101 provided, maximum is 100", exception.getMessage());
    }

    @Test
    void usesConfiguredLimit() {
        JobQueryValidator validator = new JobQueryValidator(new JobQueryProperties(2));

        assertDoesNotThrow(() -> validator.validate(Collections.nCopies(2, "job-id")));
        assertThrows(
                JobIdsLimitExceededException.class,
                () -> validator.validate(Collections.nCopies(3, "job-id"))
        );
    }

    @Test
    void mapsExceededLimitToBadRequest() {
        JobIdsLimitExceededException exception = new JobIdsLimitExceededException(3, 2);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                new ApiExceptionHandler().handleJobIdsLimitExceeded(exception).getStatusCode()
        );
    }
}
