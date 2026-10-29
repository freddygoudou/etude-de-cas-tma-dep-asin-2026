package bj.asin.inscriptions.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpServer;

// faux service d'identification pour les tests
public final class ServiceIdentificationSimule {

    private final HttpServer serveur;
    private final AtomicLong latenceMs = new AtomicLong();
    private final Set<String> npiInconnus = ConcurrentHashMap.newKeySet();

    public ServiceIdentificationSimule() {
        try {
            serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        serveur.setExecutor(Executors.newCachedThreadPool());
        serveur.createContext("/identite/", echange -> {
            String npi = echange.getRequestURI().getPath().substring("/identite/".length());
            try {
                Thread.sleep(latenceMs.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int code = npiInconnus.contains(npi) ? 404 : 200;
            byte[] corps = (code == 200 ? "{\"nom\":\"DOSSOU\",\"prenoms\":\"Sèna\"}" : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                echange.sendResponseHeaders(code, corps.length);
                echange.getResponseBody().write(corps);
            } catch (IOException ignoree) {
                // le client a abandonné (timeout), normal
            } finally {
                echange.close();
            }
        });
        serveur.start();
    }

    public String url() {
        return "http://localhost:" + serveur.getAddress().getPort();
    }

    public void latence(long ms) {
        latenceMs.set(ms);
    }

    public void declarerInconnu(String npi) {
        npiInconnus.add(npi);
    }

    public void reinitialiser() {
        latenceMs.set(0);
        npiInconnus.clear();
    }
}
