package cz.dia.ismd.assistant.exception;

import cz.dia.ismd.assistant.records.exception.ErrorResponse;
import cz.dia.ismd.assistant.service.llm.LlmException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(JobNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(SuggestionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSuggestionNotFound(SuggestionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(TokenLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleTokenLimitReached(TokenLimitReachedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ErrorResponse> handleLlmException(LlmException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request data");
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(detail));
    }
}
