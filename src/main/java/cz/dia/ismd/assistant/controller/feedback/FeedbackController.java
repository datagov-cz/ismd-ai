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

import java.util.List;

@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/accept-suggestion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@Valid @RequestBody List<@Valid FeedbackRequest> requests) {
        record(requests, FeedbackType.ACCEPTED);
    }

    @PostMapping("/like-suggestion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@Valid @RequestBody List<@Valid FeedbackRequest> requests) {
        record(requests, FeedbackType.LIKED);
    }

    @PostMapping("/dislike-suggestion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dislike(@Valid @RequestBody List<@Valid FeedbackRequest> requests) {
        record(requests, FeedbackType.DISLIKED);
    }

    private void record(List<FeedbackRequest> requests, FeedbackType type) {
        feedbackService.record(requests.stream()
                .flatMap(request -> request.toRecords(type).stream())
                .toList());
    }
}
