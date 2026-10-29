package bj.asin.inscriptions.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import bj.asin.inscriptions.config.IdentiteProperties;
import bj.asin.inscriptions.dto.ResultatIdentite;
import bj.asin.inscriptions.service.IdentificationService;

/**
 * INC-206 : avant il n'y avait aucun timeout. Ici connexion, réponse et appel complet sont limités à
 * identite.delai (5 s max), sinon on renvoie EN_ATTENTE.
 */
@Service
public class IdentificationServiceImpl implements IdentificationService {

    private static final Logger LOG = LoggerFactory.getLogger(IdentificationServiceImpl.class);

    private final IdentiteProperties proprietes;
    private final HttpClient client;
    private final ObjectMapper json;

    public IdentificationServiceImpl(IdentiteProperties proprietes, ObjectMapper json) {
        this.proprietes = proprietes;
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(proprietes.delai()).build();
    }

    @Override
    public ResultatIdentite verifier(String npi) {
        HttpRequest requete = HttpRequest.newBuilder(URI.create(proprietes.url() + "/identite/" + npi))
                .timeout(proprietes.delai())
                .GET()
                .build();
        long debut = System.nanoTime();
        try {
            HttpResponse<String> reponse = client.sendAsync(requete, HttpResponse.BodyHandlers.ofString())
                    .get(proprietes.delai().toMillis(), TimeUnit.MILLISECONDS);
            if (reponse.statusCode() == 404) {
                return ResultatIdentite.inconnu();
            }
            if (reponse.statusCode() != 200) {
                LOG.warn("appel identite npi={} -> {} : inscription mise en attente", npi, reponse.statusCode());
                return ResultatIdentite.enAttente();
            }
            JsonNode corps = json.readTree(reponse.body());
            return ResultatIdentite.connu(corps.path("nom").asText(null), corps.path("prenoms").asText(null));
        } catch (TimeoutException e) {
            LOG.warn("appel identite npi={} : délai de {} ms dépassé, inscription mise en attente",
                    npi, proprietes.delai().toMillis());
            return ResultatIdentite.enAttente();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultatIdentite.enAttente();
        } catch (ExecutionException | RuntimeException | java.io.IOException e) {
            LOG.warn("appel identite npi={} en échec ({} ms) : {}", npi,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - debut), e.toString());
            return ResultatIdentite.enAttente();
        }
    }
}
