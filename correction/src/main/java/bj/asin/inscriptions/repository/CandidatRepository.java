package bj.asin.inscriptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import bj.asin.inscriptions.entity.Candidat;

public interface CandidatRepository extends JpaRepository<Candidat, Long> {

    boolean existsByNpi(String npi);
}
