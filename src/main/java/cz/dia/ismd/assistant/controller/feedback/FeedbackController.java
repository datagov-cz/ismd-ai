package cz.dia.ismd.assistant.controller.feedback;

import cz.dia.ismd.assistant.service.feedback.FeedbackService;
import cz.dia.ismd.assistant.data.feedback.FeedbackType;
import cz.dia.ismd.assistant.dto.feedback.FeedbackRequest;
import cz.dia.ismd.assistant.service.environment.ApiEnvironment;
import cz.dia.ismd.assistant.service.mock.DevelopmentApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FeedbackController implements FeedbackApi {

    private final FeedbackService feedbackService;
    private final ApiEnvironment apiEnvironment;
    private final DevelopmentApiResponses developmentApiResponses;

    public FeedbackController(
            FeedbackService feedbackService,
            ApiEnvironment apiEnvironment,
            DevelopmentApiResponses developmentApiResponses
    ) {
        this.feedbackService = feedbackService;
        this.apiEnvironment = apiEnvironment;
        this.developmentApiResponses = developmentApiResponses;
    }

    @PostMapping("/accept-suggestion")
    @Override
    public ResponseEntity<?> accept(@Valid @RequestBody List<@Valid FeedbackRequest> requests) {
        if (apiEnvironment.isDevelopment()) {
            return ResponseEntity.ok(developmentApiResponses.acceptFeedback());
        }
        record(requests, FeedbackType.ACCEPTED);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/like-suggestion")
    @Override
    public ResponseEntity<?> like(@Valid @RequestBody List<@Valid FeedbackRequest> requests) {
        if (apiEnvironment.isDevelopment()) {
            return ResponseEntity.ok(developmentApiResponses.likeFeedback());
        }
        record(requests, FeedbackType.LIKED);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dislike-suggestion")
    @Override
    public ResponseEntity<?> dislike(@Valid @RequestBody List<@Valid FeedbackRequest> requests) {
        if (apiEnvironment.isDevelopment()) {
            return ResponseEntity.ok(developmentApiResponses.dislikeFeedback());
        }
        record(requests, FeedbackType.DISLIKED);
        return ResponseEntity.noContent().build();
    }

    private void record(List<FeedbackRequest> requests, FeedbackType type) {
        feedbackService.record(requests.stream()
                .flatMap(request -> request.toRecords(type).stream())
                .toList());
    }
}
