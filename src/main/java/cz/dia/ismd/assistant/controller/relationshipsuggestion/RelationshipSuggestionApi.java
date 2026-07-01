package cz.dia.ismd.assistant.controller.relationshipsuggestion;

import cz.dia.ismd.assistant.controller.OpenApiExamples;
import cz.dia.ismd.assistant.dto.classsuggestion.JobStartResponse;
import cz.dia.ismd.assistant.dto.relationshipsuggestion.RelationshipSuggestionJobRequest;
import cz.dia.ismd.assistant.dto.relationshipsuggestion.RelationshipSuggestionsJobResponse;
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

public interface RelationshipSuggestionApi {

    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            schema = @Schema(implementation = RelationshipSuggestionJobRequest.class),
                            examples = @ExampleObject(
                                    name = "Relationship suggestion job request",
                                    value = OpenApiExamples.RELATIONSHIP_SUGGESTION_REQUEST
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "Relationship suggestion job accepted.",
                            content = @Content(
                                    schema = @Schema(implementation = JobStartResponse.class),
                                    examples = @ExampleObject(
                                            name = "Started relationship suggestion job",
                                            value = OpenApiExamples.RELATIONSHIP_JOB_START_RESPONSE
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
    JobStartResponse startRelationshipSuggestions(
            @Parameter(example = "2024") int year,
            @Parameter(example = "1") int number,
            @Parameter(example = "2024-01-15") LocalDate date,
            Jwt jwt,
            RelationshipSuggestionJobRequest request
    );

    @Operation(
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Relationship suggestion jobs.",
                            content = @Content(
                                    array = @ArraySchema(schema = @Schema(implementation = RelationshipSuggestionsJobResponse.class)),
                                    examples = @ExampleObject(
                                            name = "Relationship suggestion job result",
                                            value = OpenApiExamples.RELATIONSHIP_SUGGESTIONS_RESPONSE
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
    List<RelationshipSuggestionsJobResponse> getRelationshipSuggestions(
            @Parameter(
                    description = "Job identifiers to retrieve.",
                    example = OpenApiExamples.RELATIONSHIP_JOB_ID
            )
            List<UUID> jobIds
    );
}
