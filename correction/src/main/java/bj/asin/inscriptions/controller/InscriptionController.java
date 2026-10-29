package bj.asin.inscriptions.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bj.asin.inscriptions.dto.DemandeInscription;
import bj.asin.inscriptions.dto.InscriptionReponse;
import bj.asin.inscriptions.service.InscriptionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inscriptions")
public class InscriptionController {

    private final InscriptionService inscriptionService;

    public InscriptionController(InscriptionService inscriptionService) {
        this.inscriptionService = inscriptionService;
    }

    @PostMapping
    public ResponseEntity<InscriptionReponse> inscrire(@Valid @RequestBody DemandeInscription demande) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inscriptionService.inscrire(demande));
    }
}
