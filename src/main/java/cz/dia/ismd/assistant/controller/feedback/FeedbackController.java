package cz.dia.ismd.assistant.controller.feedback;

import cz.dia.ismd.assistant.service.feedback.FeedbackService;
import cz.dia.ismd.assistant.data.feedback.FeedbackType;
import cz.dia.ismd.assistant.dto.feedback.FeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/accepted-suggestions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@Valid @RequestBody FeedbackRequest request) {
        feedbackService.record(request.jobId(), request.suggestionId(), FeedbackType.ACCEPTED);
    }

    @PostMapping("/liked-suggestions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@Valid @RequestBody FeedbackRequest request) {
        feedbackService.record(request.jobId(), request.suggestionId(), FeedbackType.LIKED);
    }

    @PostMapping("/disliked-suggestions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dislike(@Valid @RequestBody FeedbackRequest request) {
        feedbackService.record(request.jobId(), request.suggestionId(), FeedbackType.DISLIKED);
    }
}
