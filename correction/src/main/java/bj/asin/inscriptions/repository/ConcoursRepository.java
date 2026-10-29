package bj.asin.inscriptions.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import bj.asin.inscriptions.entity.Concours;

public interface ConcoursRepository extends JpaRepository<Concours, Long> {

    Optional<Concours> findByCode(String code);
}
