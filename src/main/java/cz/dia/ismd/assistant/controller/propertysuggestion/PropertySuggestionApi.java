package cz.dia.ismd.assistant.controller.propertysuggestion;

import cz.dia.ismd.assistant.controller.OpenApiExamples;
import cz.dia.ismd.assistant.dto.classsuggestion.JobStartResponse;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionJobRequest;
import cz.dia.ismd.assistant.dto.propertysuggestion.PropertySuggestionsJobResponse;
import cz.dia.ismd.assistant.records.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PropertySuggestionApi {

    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            schema = @Schema(implementation = PropertySuggestionJobRequest.class),
                            examples = @ExampleObject(
                                    name = "Property suggestion job request",
                                    value = OpenApiExamples.PROPERTY_SUGGESTION_REQUEST
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "Property suggestion job accepted.",
                            content = @Content(
                                    schema = @Schema(implementation = JobStartResponse.class),
                                    examples = @ExampleObject(
                                            name = "Started property suggestion job",
                                            value = OpenApiExamples.SELECTED_CLASS_JOB_START_RESPONSE
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
    JobStartResponse startPropertySuggestions(
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
            PropertySuggestionJobRequest request
    );

    @Operation(
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Property suggestion jobs.",
                            content = @Content(
                                    array = @ArraySchema(schema = @Schema(implementation = PropertySuggestionsJobResponse.class)),
                                    examples = @ExampleObject(
                                            name = "Property suggestion job result",
                                            value = OpenApiExamples.PROPERTY_SUGGESTIONS_RESPONSE
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
    List<PropertySuggestionsJobResponse> getPropertySuggestions(
            @Parameter(
                    description = "Job identifiers to retrieve.",
                    example = OpenApiExamples.PROPERTY_JOB_ID
            )
            List<UUID> jobIds
    );
}
