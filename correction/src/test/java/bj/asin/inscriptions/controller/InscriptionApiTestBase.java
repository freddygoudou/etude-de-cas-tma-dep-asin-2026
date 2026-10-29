package bj.asin.inscriptions.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import bj.asin.inscriptions.support.ServiceIdentificationSimule;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class InscriptionApiTestBase {

    static final ServiceIdentificationSimule IDENTITE = new ServiceIdentificationSimule();

    @DynamicPropertySource
    static void identite(DynamicPropertyRegistry registre) {
        registre.add("identite.url", IDENTITE::url);
    }

    record Reponse(int statut, String corps) {
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void viderLaBase() {
        IDENTITE.reinitialiser();
        jdbc.update("DELETE FROM inscriptions");
        jdbc.update("DELETE FROM candidats");
    }

    static String demande(String npi, String concours, String centre) {
        return """
                {"npi":"%s","dateNaissance":"1995-04-24","concours":"%s","centre":"%s"}"""
                .formatted(npi, concours, centre);
    }

    Reponse poster(String corps) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/inscriptions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(corps))
                .build();
        HttpResponse<String> r = http.send(requete, HttpResponse.BodyHandlers.ofString());
        return new Reponse(r.statusCode(), r.body());
    }

    // toutes les requêtes partent en même temps (latch)
    List<Reponse> posterSimultanement(List<String> corps) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(corps.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Reponse>> futurs = new ArrayList<>();
            for (String c : corps) {
                Callable<Reponse> tache = () -> {
                    depart.await();
                    return poster(c);
                };
                futurs.add(pool.submit(tache));
            }
            depart.countDown();
            List<Reponse> reponses = new ArrayList<>();
            for (Future<Reponse> f : futurs) {
                reponses.add(f.get());
            }
            return reponses;
        } finally {
            pool.shutdownNow();
        }
    }
}
