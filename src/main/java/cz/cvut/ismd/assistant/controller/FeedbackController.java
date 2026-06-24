package cz.cvut.ismd.assistant.controller;

import cz.cvut.ismd.assistant.service.FeedbackService;
import cz.cvut.ismd.assistant.data.FeedbackType;
import cz.cvut.ismd.assistant.controller.dto.FeedbackRequest;
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
