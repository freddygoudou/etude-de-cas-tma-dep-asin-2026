package bj.asin.inscriptions.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bj.asin.inscriptions.dto.InscritDto;
import bj.asin.inscriptions.service.InscriptionService;

@RestController
@RequestMapping("/api/centres")
public class CentreController {

    private final InscriptionService inscriptionService;

    public CentreController(InscriptionService inscriptionService) {
        this.inscriptionService = inscriptionService;
    }

    @GetMapping("/{code}/inscrits")
    public List<InscritDto> inscrits(@PathVariable String code, @RequestParam String concours) {
        return inscriptionService.listerInscrits(code, concours);
    }
}
