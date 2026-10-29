package bj.asin.inscriptions.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bj.asin.inscriptions.dto.InscritDto;
import bj.asin.inscriptions.entity.Inscription;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    boolean existsByNpiAndConcours_Id(String npi, Long concoursId);

    @Query("""
            select count(i) > 0 from Inscription i
            where i.npi = :npi and i.concours.dateEpreuve = :dateEpreuve and i.concours.id <> :concoursId""")
    boolean existsAutreConcoursLeMemeJour(@Param("npi") String npi, @Param("concoursId") Long concoursId,
            @Param("dateEpreuve") LocalDate dateEpreuve);

    long countByCentre_Id(Long centreId);

    // à appeler sous verrou du centre
    @Query(value = "SELECT MAX(CAST(SUBSTRING(numero_table FROM 3) AS INTEGER)) FROM inscriptions WHERE centre_id = :centreId",
            nativeQuery = true)
    Integer plusGrandNumero(@Param("centreId") Long centreId);

    // une seule requête au lieu d'une par inscrit (INC-207)
    @Query("""
            select new bj.asin.inscriptions.dto.InscritDto(i.numeroTable, i.npi, c.nom, c.prenoms, i.statut)
            from Inscription i
            left join Candidat c on c.npi = i.npi
            where i.centre.code = :centre and i.concours.code = :concours
            order by i.numeroTable""")
    List<InscritDto> inscrits(@Param("centre") String centre, @Param("concours") String concours);
}
