package cz.dia.ismd.assistant.api;

import cz.dia.ismd.assistant.exception.JobNotFoundException;
import cz.dia.ismd.assistant.exception.InvalidVocabularyRequestException;
import cz.dia.ismd.assistant.exception.VocabularyJobCapacityException;
import cz.dia.ismd.assistant.exception.JobIdsLimitExceededException;
import cz.dia.ismd.assistant.exception.SparqlAccessException;
import cz.dia.ismd.assistant.exception.SuggestionNotFoundException;
import cz.dia.ismd.assistant.exception.TokenLimitReachedException;
import cz.dia.ismd.assistant.exception.LlmException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidVocabularyRequestException.class)
    public ResponseEntity<ErrorResponse> handleVocabularyValidation(InvalidVocabularyRequestException exception) {
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(VocabularyJobCapacityException.class)
    public ResponseEntity<ErrorResponse> handleVocabularyCapacity(VocabularyJobCapacityException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(JobIdsLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleJobIdsLimitExceeded(JobIdsLimitExceededException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

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

    @ExceptionHandler(SparqlAccessException.class)
    public ResponseEntity<ErrorResponse> handleSparqlAccessException(SparqlAccessException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElseGet(() -> exception.getBindingResult().getGlobalErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Invalid request data"));
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(detail));
    }
}
