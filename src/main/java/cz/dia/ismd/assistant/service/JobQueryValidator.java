package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.config.JobQueryProperties;
import cz.dia.ismd.assistant.exception.JobIdsLimitExceededException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobQueryValidator {

    private final JobQueryProperties properties;

    public JobQueryValidator(JobQueryProperties properties) {
        this.properties = properties;
    }

    public void validate(List<?> jobIds) {
        if (jobIds.size() > properties.maxJobIds()) {
            throw new JobIdsLimitExceededException(jobIds.size(), properties.maxJobIds());
        }
    }
}
