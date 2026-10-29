package bj.asin.inscriptions.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inscriptions")
public class Inscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concours_id")
    private Concours concours;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centre_id")
    private Centre centre;

    @Column(nullable = false)
    private String npi;

    @Column(name = "numero_table", nullable = false)
    private String numeroTable;

    @Column(nullable = false)
    private String statut;

    // recopiée du concours : rend la règle « même jour » contraignable en base
    @Column(name = "date_epreuve", nullable = false)
    private LocalDate dateEpreuve;

    // UTC
    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    protected Inscription() {
    }

    public Inscription(Concours concours, Centre centre, String npi, String numeroTable, StatutInscription statut,
            LocalDateTime creeLe) {
        this.concours = concours;
        this.centre = centre;
        this.npi = npi;
        this.numeroTable = numeroTable;
        this.statut = statut.valeur();
        this.dateEpreuve = concours.getDateEpreuve();
        this.creeLe = creeLe;
    }

    public Long getId() {
        return id;
    }

    public Concours getConcours() {
        return concours;
    }

    public Centre getCentre() {
        return centre;
    }

    public String getNpi() {
        return npi;
    }

    public String getNumeroTable() {
        return numeroTable;
    }

    public String getStatut() {
        return statut;
    }

    public LocalDate getDateEpreuve() {
        return dateEpreuve;
    }

    public LocalDateTime getCreeLe() {
        return creeLe;
    }
}
