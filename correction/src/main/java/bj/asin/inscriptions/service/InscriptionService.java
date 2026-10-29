package bj.asin.inscriptions.service;

import java.util.List;

import bj.asin.inscriptions.dto.DemandeInscription;
import bj.asin.inscriptions.dto.InscriptionReponse;
import bj.asin.inscriptions.dto.InscritDto;

public interface InscriptionService {

    InscriptionReponse inscrire(DemandeInscription demande);

    List<InscritDto> listerInscrits(String centre, String concours);
}
