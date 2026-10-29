package bj.asin.inscriptions.service.impl;

import static bj.asin.inscriptions.exception.InscriptionRefuseeException.conflit;

import java.time.Clock;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import bj.asin.inscriptions.dto.DemandeInscription;
import bj.asin.inscriptions.dto.InscriptionReponse;
import bj.asin.inscriptions.dto.InscritDto;
import bj.asin.inscriptions.dto.ResultatIdentite;
import bj.asin.inscriptions.entity.Candidat;
import bj.asin.inscriptions.entity.Centre;
import bj.asin.inscriptions.entity.Concours;
import bj.asin.inscriptions.entity.Inscription;
import bj.asin.inscriptions.entity.StatutInscription;
import bj.asin.inscriptions.exception.InscriptionRefuseeException;
import bj.asin.inscriptions.regle.RegleAge;
import bj.asin.inscriptions.regle.RegleCloture;
import bj.asin.inscriptions.repository.CandidatRepository;
import bj.asin.inscriptions.repository.CentreRepository;
import bj.asin.inscriptions.repository.ConcoursRepository;
import bj.asin.inscriptions.repository.InscriptionRepository;
import bj.asin.inscriptions.service.IdentificationService;
import bj.asin.inscriptions.service.InscriptionService;
import bj.asin.inscriptions.util.DateUtils;

/**
 * L'appel identité (étape 7) est fait avant la transaction pour ne pas garder de verrou pendant un appel
 * externe. Les étapes 4, 5, 6 et 8 sont faites ensemble sous verrou du centre. Les contraintes UNIQUE
 * couvrent le reste (même NPI sur deux centres en même temps).
 */
@Service
public class InscriptionServiceImpl implements InscriptionService {

    private final ConcoursRepository concoursRepository;
    private final CentreRepository centreRepository;
    private final CandidatRepository candidatRepository;
    private final InscriptionRepository inscriptionRepository;
    private final IdentificationService identification;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InscriptionServiceImpl(ConcoursRepository concoursRepository, CentreRepository centreRepository,
            CandidatRepository candidatRepository, InscriptionRepository inscriptionRepository,
            IdentificationService identification, TransactionTemplate transaction, Clock clock) {
        this.concoursRepository = concoursRepository;
        this.centreRepository = centreRepository;
        this.candidatRepository = candidatRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.identification = identification;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public InscriptionReponse inscrire(DemandeInscription demande) {
        Concours concours = concoursRepository.findByCode(demande.concours())
                .orElseThrow(() -> new InscriptionRefuseeException(HttpStatus.BAD_REQUEST, "Concours inconnu."));
        Centre centre = centreRepository.findByCode(demande.centre())
                .orElseThrow(() -> new InscriptionRefuseeException(HttpStatus.BAD_REQUEST, "Centre inconnu."));

        if (!RegleCloture.inscriptionsOuvertes(concours.getClotureUtc(), clock)) {
            throw conflit(InscriptionRefuseeException.CLOSES);
        }
        if (!RegleAge.estEligible(demande.dateNaissance(), concours.getDateEpreuve())) {
            throw conflit(InscriptionRefuseeException.AGE);
        }
        // contrôle rapide pour éviter un appel identité inutile (refait sous verrou après)
        verifierUnicite(demande.npi(), concours);

        ResultatIdentite identite = identification.verifier(demande.npi());
        if (identite.verdict() == ResultatIdentite.Verdict.INCONNU) {
            throw conflit(InscriptionRefuseeException.NPI_INCONNU);
        }
        StatutInscription statut = identite.verdict() == ResultatIdentite.Verdict.CONNU
                ? StatutInscription.VALIDEE
                : StatutInscription.EN_ATTENTE_VERIFICATION;

        try {
            return transaction.execute(tx -> enregistrer(demande, concours, centre, identite, statut));
        } catch (DataIntegrityViolationException premiereTentative) {
            // même NPI écrit en parallèle sur un autre centre : on rejoue une fois pour avoir le bon message
            try {
                return transaction.execute(tx -> enregistrer(demande, concours, centre, identite, statut));
            } catch (DataIntegrityViolationException e) {
                throw conflit(InscriptionRefuseeException.DEJA_INSCRIT);
            }
        }
    }

    @Override
    public List<InscritDto> listerInscrits(String centre, String concours) {
        return inscriptionRepository.inscrits(centre, concours);
    }

    private InscriptionReponse enregistrer(DemandeInscription demande, Concours concours, Centre centre,
            ResultatIdentite identite, StatutInscription statut) {
        Centre verrouille = centreRepository.verrouiller(centre.getId());

        verifierUnicite(demande.npi(), concours);
        if (inscriptionRepository.countByCentre_Id(verrouille.getId()) >= verrouille.getCapacite()) {
            throw conflit(InscriptionRefuseeException.COMPLET);
        }
        Integer max = inscriptionRepository.plusGrandNumero(verrouille.getId());
        String numero = "%s-%04d".formatted(verrouille.getCode(), (max == null ? 0 : max) + 1);

        if (identite.verdict() == ResultatIdentite.Verdict.CONNU && !candidatRepository.existsByNpi(demande.npi())) {
            candidatRepository.saveAndFlush(new Candidat(demande.npi(), identite.nom(), identite.prenoms(),
                    demande.dateNaissance(), DateUtils.enUtc(clock.instant())));
        }
        // en attente : candidat créé plus tard, à la revérification
        // saveAndFlush : une violation de contrainte UNIQUE remonte ici, pas au commit
        Inscription inscription = inscriptionRepository.saveAndFlush(new Inscription(concours, verrouille,
                demande.npi(), numero, statut, DateUtils.enUtc(clock.instant())));
        return new InscriptionReponse(inscription.getId(), concours.getCode(), verrouille.getCode(), demande.npi(),
                numero, statut.valeur());
    }

    private void verifierUnicite(String npi, Concours concours) {
        if (inscriptionRepository.existsByNpiAndConcours_Id(npi, concours.getId())) {
            throw conflit(InscriptionRefuseeException.DEJA_INSCRIT);
        }
        if (inscriptionRepository.existsAutreConcoursLeMemeJour(npi, concours.getId(), concours.getDateEpreuve())) {
            throw conflit(InscriptionRefuseeException.MEME_JOUR);
        }
    }
}
