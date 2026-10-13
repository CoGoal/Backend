package edu.stankin.cogoalauth.web.controllers;
import edu.stankin.cogoalauth.exception.*;
import edu.stankin.cogoalauth.web.error.ApiError;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.List;
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) { return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage())); }
    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> authentication(AuthenticationFailedException e) {
        return ResponseEntity.status(e instanceof AccountLockedException ? 423 : 401)
                .body(ApiError.of(e instanceof AccountLockedException ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS", e.getMessage()));
    }
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> missing(NotFoundException e) { return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "Пользователь не найден")); }
    @ExceptionHandler({BusinessRuleException.class, DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(RuntimeException e) { return ResponseEntity.status(409).body(ApiError.of("CONFLICT", "Запрос конфликтует с текущим состоянием данных")); }
    @ExceptionHandler(InvalidFieldException.class)
    ResponseEntity<ApiError> field(InvalidFieldException e) { return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Проверьте заполнение полей", List.of(new ApiError.FieldError(e.getField(), "Некорректное значение")))); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> denied(RuntimeException e) { return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN", "Недостаточно прав")); }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders h, HttpStatusCode s, WebRequest r) {
        var details = e.getBindingResult().getFieldErrors().stream().map(f -> new ApiError.FieldError(f.getField(), "Некорректное значение")).distinct().toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Проверьте заполнение полей", details));
    }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders h, HttpStatusCode s, WebRequest r) {
        return ResponseEntity.status(s).headers(h).body(ApiError.of("REQUEST_ERROR", "Некорректный запрос"));
    }
}
