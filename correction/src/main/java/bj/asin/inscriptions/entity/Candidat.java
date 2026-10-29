package bj.asin.inscriptions.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidats")
public class Candidat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String npi;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenoms;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    // UTC
    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    protected Candidat() {
    }

    public Candidat(String npi, String nom, String prenoms, LocalDate dateNaissance, LocalDateTime creeLe) {
        this.npi = npi;
        this.nom = nom;
        this.prenoms = prenoms;
        this.dateNaissance = dateNaissance;
        this.creeLe = creeLe;
    }

    public Long getId() {
        return id;
    }

    public String getNpi() {
        return npi;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenoms() {
        return prenoms;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public LocalDateTime getCreeLe() {
        return creeLe;
    }
}
