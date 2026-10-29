package bj.asin.inscriptions.service;

import bj.asin.inscriptions.dto.ResultatIdentite;

public interface IdentificationService {

    ResultatIdentite verifier(String npi);
}
