package org.sysml.ast.exception;

import org.sysml.ast.model.Diagnostic;
import org.sysml.ast.model.DiagnosticSeverity;
import org.sysml.ast.model.ParseResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ParseException.class)
    public ResponseEntity<ParseResult> handleParseException(ParseException ex) {
        ParseResult result = ParseResult.builder()
                .success(false)
                .diagnostics(List.of(Diagnostic.builder()
                        .severity(DiagnosticSeverity.ERROR)
                        .message(ex.getMessage())
                        .build()))
                .build();
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ParseResult> handleGenericException(Exception ex) {
        ParseResult result = ParseResult.builder()
                .success(false)
                .diagnostics(List.of(Diagnostic.builder()
                        .severity(DiagnosticSeverity.ERROR)
                        .message("Internal server error: " + ex.getMessage())
                        .build()))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
