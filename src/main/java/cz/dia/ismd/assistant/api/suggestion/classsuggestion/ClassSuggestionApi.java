package cz.dia.ismd.assistant.api.suggestion.classsuggestion;

import cz.dia.ismd.assistant.api.ErrorResponse;
import cz.dia.ismd.assistant.api.OpenApiExamples;
import cz.dia.ismd.assistant.api.job.JobStartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClassSuggestionApi {

    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            schema = @Schema(implementation = ClassSuggestionJobRequest.class),
                            examples = @ExampleObject(
                                    name = "Class suggestion job request",
                                    value = OpenApiExamples.CLASS_SUGGESTION_REQUEST
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "Class suggestion job accepted.",
                            content = @Content(
                                    schema = @Schema(implementation = JobStartResponse.class),
                                    examples = @ExampleObject(
                                            name = "Started class suggestion job",
                                            value = OpenApiExamples.CLASS_SUGGESTION_START_RESPONSE
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Invalid request data.",
                            content = @Content(
                                    schema = @Schema(implementation = ErrorResponse.class),
                                    examples = @ExampleObject(
                                            name = "Validation error",
                                            value = OpenApiExamples.VALIDATION_ERROR_RESPONSE
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "429",
                            description = "Token limit reached.",
                            content = @Content(
                                    schema = @Schema(implementation = ErrorResponse.class),
                                    examples = @ExampleObject(
                                            name = "Token limit error",
                                            value = OpenApiExamples.TOKEN_LIMIT_ERROR_RESPONSE
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "502",
                            description = "LLM provider error.",
                            content = @Content(
                                    schema = @Schema(implementation = ErrorResponse.class),
                                    examples = @ExampleObject(
                                            name = "LLM error",
                                            value = OpenApiExamples.LLM_ERROR_RESPONSE
                                    )
                            )
                    )
            }
    )
    JobStartResponse startClassSuggestions(
            @Parameter(
                    description = "Publication year of the legal act to use as suggestion context. The example described is for legal act 1/2024.",
                    example = "2024"
            )
            int year,
            @Parameter(
                    description = "Official number of the legal act within the publication year. The example described is for legal act 1/2024.",
                    example = "1"
            )
            int number,
            @Parameter(
                    description = "Date version of the legal act to use for the suggestion job (znění).",
                    example = "2024-01-15"
            )
            LocalDate date,
            Jwt jwt,
            @Valid @RequestBody ClassSuggestionJobRequest request
    );

    @Operation(
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Class suggestion jobs.",
                            content = @Content(
                                    array = @ArraySchema(schema = @Schema(implementation = ClassSuggestionsJobResponse.class)),
                                    examples = @ExampleObject(
                                            name = "Class suggestion job result",
                                            value = OpenApiExamples.CLASS_SUGGESTIONS_RESPONSE
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "One of the requested jobs was not found.",
                            content = @Content(
                                    schema = @Schema(implementation = ErrorResponse.class),
                                    examples = @ExampleObject(
                                            name = "Job not found",
                                            value = OpenApiExamples.JOB_NOT_FOUND_ERROR_RESPONSE
                                    )
                            )
                    )
            }
    )
    List<ClassSuggestionsJobResponse> getClassSuggestions(
            @Parameter(
                    description = "Job identifiers to retrieve.",
                    example = OpenApiExamples.JOB_ID
            )
            List<UUID> jobIds
    );
}
