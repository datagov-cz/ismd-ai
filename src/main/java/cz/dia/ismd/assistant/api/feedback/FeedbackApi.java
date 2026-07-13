package cz.dia.ismd.assistant.api.feedback;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface FeedbackApi {

    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = FeedbackRequest.class)),
                            examples = @ExampleObject(
                                    name = "Accepted suggestion feedback request",
                                    value = OpenApiExamples.FEEDBACK_REQUEST
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Feedback recorded."),
            @ApiResponse(
                    responseCode = "200",
                    description = "Development response.",
                    content = @Content(
                            schema = @Schema(implementation = FeedbackResponse.class),
                            examples = @ExampleObject(
                                    name = "Accepted feedback response",
                                    value = OpenApiExamples.FEEDBACK_ACCEPT_RESPONSE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Feedback references a job or suggestion that was not found.",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Job not found",
                                            value = OpenApiExamples.FEEDBACK_JOB_NOT_FOUND_ERROR_RESPONSE
                                    ),
                                    @ExampleObject(
                                            name = "Suggestion not found",
                                            value = OpenApiExamples.FEEDBACK_SUGGESTION_NOT_FOUND_ERROR_RESPONSE
                                    )
                            }
                    )
            )
    })
    ResponseEntity<?> accept(@Valid @RequestBody List<@Valid FeedbackRequest> requests);

    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = FeedbackRequest.class)),
                            examples = @ExampleObject(
                                    name = "Liked suggestion feedback request",
                                    value = OpenApiExamples.FEEDBACK_REQUEST
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Feedback recorded."),
            @ApiResponse(
                    responseCode = "200",
                    description = "Development response.",
                    content = @Content(
                            schema = @Schema(implementation = FeedbackResponse.class),
                            examples = @ExampleObject(
                                    name = "Liked feedback response",
                                    value = OpenApiExamples.FEEDBACK_LIKE_RESPONSE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Feedback references a job or suggestion that was not found.",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Job not found",
                                            value = OpenApiExamples.FEEDBACK_JOB_NOT_FOUND_ERROR_RESPONSE
                                    ),
                                    @ExampleObject(
                                            name = "Suggestion not found",
                                            value = OpenApiExamples.FEEDBACK_SUGGESTION_NOT_FOUND_ERROR_RESPONSE
                                    )
                            }
                    )
            )
    })
    ResponseEntity<?> like(@Valid @RequestBody List<@Valid FeedbackRequest> requests);

    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = FeedbackRequest.class)),
                            examples = @ExampleObject(
                                    name = "Disliked suggestion feedback request",
                                    value = OpenApiExamples.FEEDBACK_REQUEST
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Feedback recorded."),
            @ApiResponse(
                    responseCode = "200",
                    description = "Development response.",
                    content = @Content(
                            schema = @Schema(implementation = FeedbackResponse.class),
                            examples = @ExampleObject(
                                    name = "Disliked feedback response",
                                    value = OpenApiExamples.FEEDBACK_DISLIKE_RESPONSE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Feedback references a job or suggestion that was not found.",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Job not found",
                                            value = OpenApiExamples.FEEDBACK_JOB_NOT_FOUND_ERROR_RESPONSE
                                    ),
                                    @ExampleObject(
                                            name = "Suggestion not found",
                                            value = OpenApiExamples.FEEDBACK_SUGGESTION_NOT_FOUND_ERROR_RESPONSE
                                    )
                            }
                    )
            )
    })
    ResponseEntity<?> dislike(@Valid @RequestBody List<@Valid FeedbackRequest> requests);
}
