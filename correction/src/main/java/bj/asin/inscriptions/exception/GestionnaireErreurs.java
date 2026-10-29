package bj.asin.inscriptions.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GestionnaireErreurs {

    @ExceptionHandler(InscriptionRefuseeException.class)
    ResponseEntity<Map<String, String>> refus(InscriptionRefuseeException e) {
        return ResponseEntity.status(e.statut()).body(Map.of("erreur", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> formeInvalide(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(" ; "));
        return ResponseEntity.badRequest().body(Map.of("erreur", message));
    }
}
