package bj.asin.inscriptions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "centres")
public class Centre {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private int capacite;

    protected Centre() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public int getCapacite() {
        return capacite;
    }
}
