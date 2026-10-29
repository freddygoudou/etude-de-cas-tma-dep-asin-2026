package bj.asin.inscriptions.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

public record DemandeInscription(
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Le NPI doit comporter exactement 10 chiffres.") String npi,
        @NotNull @Past LocalDate dateNaissance,
        @NotBlank String concours,
        @NotBlank @Pattern(regexp = "[A-Z]", message = "Code de centre invalide.") String centre) {
}
