package bj.asin.inscriptions.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "concours")
public class Concours {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "date_epreuve", nullable = false)
    private LocalDate dateEpreuve;

    // instant UTC stocké sans fuseau
    @Column(name = "cloture", nullable = false)
    private LocalDateTime clotureUtc;

    protected Concours() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public LocalDate getDateEpreuve() {
        return dateEpreuve;
    }

    public LocalDateTime getClotureUtc() {
        return clotureUtc;
    }
}
