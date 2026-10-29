package bj.asin.inscriptions.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bj.asin.inscriptions.entity.Centre;
import jakarta.persistence.LockModeType;

public interface CentreRepository extends JpaRepository<Centre, Long> {

    Optional<Centre> findByCode(String code);

    // SELECT ... FOR UPDATE : verrou sur le centre jusqu'à la fin de la transaction (INC-201, INC-202)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Centre c where c.id = :id")
    Centre verrouiller(@Param("id") Long id);
}
