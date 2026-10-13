package edu.stankin.cogoalmain.web.controllers;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields=exception.getBindingResult().getFieldErrors().stream()
                .map(error->Map.of("field",error.getField(),"message","Некорректное значение")).distinct().toList();
        return ResponseEntity.badRequest().body(Map.of("code","VALIDATION_ERROR","message","Проверьте заполнение полей","details",fields));
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).headers(headers)
                .body(Map.of("code","REQUEST_ERROR","message","Некорректный запрос","details",List.of()));
    }
}
