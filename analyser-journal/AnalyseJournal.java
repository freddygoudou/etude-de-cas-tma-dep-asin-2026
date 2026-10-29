import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyse du journal de prod.
 * Usage : java analyser-journal/AnalyseJournal.java journal_production.log
 * Heures affichées en heure du Bénin (UTC+1).
 */
public class AnalyseJournal {

    static final ZoneOffset BENIN = ZoneOffset.ofHours(1);
    static final DateTimeFormatter FORMAT_BJ = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(BENIN);
    static final long SEUIL_LENT_MS = 5_000;
    static final int AGE_MAX = 35;

    // repris de la table concours (clôture en UTC)
    record Concours(LocalDate dateEpreuve, Instant clotureUtc) {
    }

    static final Map<String, Concours> CONCOURS = Map.of(
            "DOUANES", new Concours(LocalDate.of(2026, 12, 12), Instant.parse("2026-11-30T22:59:00Z")),
            "IMPOTS", new Concours(LocalDate.of(2026, 12, 12), Instant.parse("2026-11-30T22:59:00Z")),
            "TRESOR", new Concours(LocalDate.of(2026, 12, 19), Instant.parse("2026-11-20T22:59:00Z")));

    static final Pattern LIGNE = Pattern.compile("^(\\S+)\\s+([A-Z]+)\\s+\\[req:([0-9a-f]+)\\]\\s+(.*)$");
    static final Pattern APPEL_IDENTITE =
            Pattern.compile("^appel identite GET /identite/(\\S+) -> (\\d+) \\((\\d+) ms\\)");
    static final Pattern REQUETE =
            Pattern.compile("^(\\S+) ([A-Z]+) (\\S+) -> (\\d+) \\((\\d+) ms\\)\\s*(.*)$");
    static final Pattern ATTRIBUT = Pattern.compile("(\\w+)=(\"([^\"]*)\"|\\S+)");

    record Requete(Instant fin, String req, String ip, String methode, String chemin, int code, long ms,
            Map<String, String> attrs) {
        Instant debut() {
            return fin.minusMillis(ms);
        }

        String attr(String cle) {
            return attrs.getOrDefault(cle, "");
        }
    }

    record AppelIdentite(Instant fin, String req, String npi, int code, long ms) {
        Instant debut() {
            return fin.minusMillis(ms);
        }
    }

    final List<Requete> requetes = new ArrayList<>();
    final Map<String, AppelIdentite> appels = new HashMap<>();
    int lignesIgnorees;

    static String bj(Instant instant) {
        return FORMAT_BJ.format(instant);
    }

    void lire(Path chemin) throws IOException {
        for (String brut : Files.readAllLines(chemin, StandardCharsets.UTF_8)) {
            Matcher m = LIGNE.matcher(brut);
            if (!m.matches()) {
                lignesIgnorees++;
                continue;
            }
            Instant ts = Instant.parse(m.group(1));
            String req = m.group(3);
            String reste = m.group(4);
            Matcher a = APPEL_IDENTITE.matcher(reste);
            if (a.find()) {
                appels.put(req, new AppelIdentite(ts, req, a.group(1), Integer.parseInt(a.group(2)),
                        Long.parseLong(a.group(3))));
                continue;
            }
            Matcher r = REQUETE.matcher(reste);
            if (!r.matches()) {
                lignesIgnorees++;
                continue;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            Matcher at = ATTRIBUT.matcher(r.group(6));
            while (at.find()) {
                attrs.put(at.group(1), at.group(3) != null ? at.group(3) : at.group(2));
            }
            requetes.add(new Requete(ts, req, r.group(1), r.group(2), r.group(3), Integer.parseInt(r.group(4)),
                    Long.parseLong(r.group(5)), attrs));
        }
    }

    List<Requete> inscriptions() {
        return requetes.stream().filter(r -> r.methode().equals("POST") && r.chemin().equals("/api/inscriptions"))
                .toList();
    }

    static <K> Map<K, List<Requete>> grouper(List<Requete> rs, Function<Requete, K> cle) {
        return rs.stream().collect(Collectors.groupingBy(cle, TreeMap::new, Collectors.toList()));
    }

    void numerosEnDouble(List<Requete> acceptees) {
        Map<String, List<Requete>> parNumero = grouper(acceptees, r -> r.attr("numero"));
        parNumero.values().removeIf(l -> l.size() < 2);
        System.out.printf("1. Numéros de table attribués plusieurs fois : %d numéros, %d attributions%n",
                parNumero.size(), parNumero.values().stream().mapToInt(List::size).sum());
        parNumero.forEach((numero, rs) -> System.out.printf("   %s x%d : %s%n", numero, rs.size(), rs.stream()
                .map(r -> "%s %s npi=%s".formatted(bj(r.fin()), r.attr("concours"), r.attr("npi")))
                .collect(Collectors.joining(" | "))));
    }

    void inscriptionsEnDouble(List<Requete> acceptees) {
        Map<String, List<Requete>> parCle = grouper(acceptees, r -> r.attr("npi") + " " + r.attr("concours"));
        parCle.values().removeIf(l -> l.size() < 2);
        System.out.printf("%n2. Inscriptions en double : %d candidats, %d inscriptions en trop%n", parCle.size(),
                parCle.values().stream().mapToInt(l -> l.size() - 1).sum());
        parCle.forEach((cle, rs) -> System.out.printf("   npi=%s ip=%s : %s%n", cle, rs.get(0).ip(), rs.stream()
                .map(r -> "%s %s (%d ms)".formatted(bj(r.fin()), r.attr("numero"), r.ms()))
                .collect(Collectors.joining(" | "))));
    }

    void lenteurIdentification() {
        List<AppelIdentite> lents = appels.values().stream().filter(a -> a.ms() >= SEUIL_LENT_MS)
                .sorted(Comparator.comparing(AppelIdentite::debut)).toList();
        System.out.printf("%n3. Service d'identification >= 5 s%n");
        if (lents.isEmpty()) {
            System.out.println("   aucune");
            return;
        }
        Instant debut = lents.stream().map(AppelIdentite::debut).min(Comparator.naturalOrder()).orElseThrow();
        Instant fin = lents.stream().map(AppelIdentite::fin).max(Comparator.naturalOrder()).orElseThrow();
        Map<String, Requete> parReq = requetes.stream()
                .collect(Collectors.toMap(Requete::req, r -> r, (x, y) -> x));
        List<Requete> touchees = lents.stream().map(a -> parReq.get(a.req())).filter(r -> r != null).toList();
        long posts = touchees.stream().filter(r -> r.methode().equals("POST")).count();
        long convocations = touchees.stream().filter(r -> r.chemin().endsWith("/convocation")).count();
        long accepteesPost = touchees.stream().filter(r -> r.methode().equals("POST") && r.code() == 201).count();
        long enCours = requetes.stream().filter(r -> !r.debut().isAfter(fin) && !r.fin().isBefore(debut)).count();
        System.out.printf("   Période : du %s au %s (%.1f min)%n", bj(debut), bj(fin),
                Duration.between(debut, fin).toMillis() / 60_000.0);
        System.out.printf("   Appels lents : %d ; requêtes touchées : %d (inscriptions (POST) %d, convocations %d)%n",
                lents.size(), touchees.size(), posts, convocations);
        System.out.printf("   Inscriptions acceptées pendant la lenteur : %d ; appel le plus long %d ms, "
                + "requête la plus longue %d ms%n", accepteesPost,
                lents.stream().mapToLong(AppelIdentite::ms).max().orElse(0),
                touchees.stream().mapToLong(Requete::ms).max().orElse(0));
        System.out.printf("   Requêtes en cours pendant la période (toutes routes) : %d%n", enCours);
    }

    void clotureTresor(List<Requete> posts) {
        Instant cloture = CONCOURS.get("TRESOR").clotureUtc();
        List<Requete> refus = posts.stream()
                .filter(r -> r.attr("concours").equals("TRESOR") && r.attr("erreur").contains("closes")).toList();
        List<Requete> aTort = refus.stream().filter(r -> !r.fin().isAfter(cloture)).toList();
        Set<String> candidats = aTort.stream().map(r -> r.attr("npi")).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Long> parCentre = aTort.stream()
                .collect(Collectors.groupingBy(r -> r.attr("centre"), TreeMap::new, Collectors.counting()));
        System.out.printf("%n4. Clôture du Trésor%n");
        System.out.printf("   Clôture officielle : %s ; premier refus « Inscriptions closes » : %s%n", bj(cloture),
                refus.stream().map(Requete::fin).min(Comparator.naturalOrder()).map(AnalyseJournal::bj).orElse("-"));
        System.out.printf("   Refus à tort : %d requêtes, %d candidats distincts (requêtes par centre : %s) ; "
                + "refus légitimes après clôture : %d%n", aTort.size(), candidats.size(), parCentre,
                refus.size() - aTort.size());
    }

    void refusAge(List<Requete> posts) {
        List<Requete> refus = posts.stream().filter(r -> r.attr("erreur").contains("ge limite")).toList();
        List<String> aTort = new ArrayList<>();
        for (Requete r : refus) {
            LocalDate naissance = LocalDate.parse(r.attr("naissance"));
            int annee = CONCOURS.get(r.attr("concours")).dateEpreuve().getYear();
            LocalDate reference = LocalDate.of(annee, 12, 31);
            int age = annee - naissance.getYear();
            double calculApplique = (reference.toEpochDay() - naissance.toEpochDay()) / 365.0;
            if (age <= AGE_MAX) {
                aTort.add("   %s npi=%s %s centre=%s né(e) le %s : %d ans au 31/12 (calcul appliqué %.3f)".formatted(
                        bj(r.fin()), r.attr("npi"), r.attr("concours"), r.attr("centre"), naissance, age,
                        calculApplique));
            }
        }
        System.out.printf("%n5. Refus pour l'âge : %d au total, dont %d à tort%n", refus.size(), aTort.size());
        aTort.forEach(System.out::println);
    }

    void listeInscrits() {
        Pattern liste = Pattern.compile("^/api/centres/(\\w+)/inscrits");
        Map<String, List<Requete>> parCentre = new TreeMap<>();
        for (Requete r : requetes) {
            Matcher m = liste.matcher(r.chemin());
            if (r.methode().equals("GET") && m.find()) {
                parCentre.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(r);
            }
        }
        System.out.printf("%n6. Liste des inscrits par centre%n");
        System.out.println("   centre  appels  moyenne(ms)  max(ms)  >2s  lignes max");
        parCentre.forEach((centre, rs) -> System.out.printf("   %-6s  %6d  %11d  %7d  %3d  %10d%n", centre, rs.size(),
                Math.round(rs.stream().mapToLong(Requete::ms).average().orElse(0)),
                rs.stream().mapToLong(Requete::ms).max().orElse(0),
                rs.stream().filter(r -> r.ms() > 2_000).count(),
                rs.stream().mapToInt(r -> Integer.parseInt(r.attrs().getOrDefault("lignes", "0"))).max().orElse(0)));
    }

    void rechercheSuspecte() {
        Pattern suspect = Pattern.compile("['\";]|--|\\bunion\\b|\\bselect\\b|\\bor\\b\\s+'", Pattern.CASE_INSENSITIVE);
        List<String> lignes = new ArrayList<>();
        for (Requete r : requetes) {
            if (!r.chemin().startsWith("/api/inscrits/recherche")) {
                continue;
            }
            String nom = URLDecoder.decode(r.chemin().substring(r.chemin().indexOf("nom=") + 4), StandardCharsets.UTF_8);
            if (suspect.matcher(nom).find()) {
                lignes.add("   %s %s -> %d lignes=%s nom=\"%s\"".formatted(bj(r.fin()), r.ip(), r.code(),
                        r.attrs().getOrDefault("lignes", "-"), nom));
            }
        }
        System.out.printf("%nBonus. Recherches suspectes (injection SQL) : %d%n", lignes.size());
        lignes.forEach(System.out::println);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage : java analyser-journal/AnalyseJournal.java <chemin/du/journal.log>");
            System.exit(2);
        }
        Path journal = Path.of(args[0]);
        if (!Files.isRegularFile(journal)) {
            System.err.println("Journal introuvable : " + journal.toAbsolutePath());
            System.exit(1);
        }
        AnalyseJournal analyse = new AnalyseJournal();
        analyse.lire(journal);
        List<Requete> posts = analyse.inscriptions();
        List<Requete> acceptees = posts.stream().filter(r -> r.code() == 201).toList();

        System.out.printf("Analyse de %s (heures du Bénin, UTC+1) ; lignes non reconnues : %d%n%n", args[0],
                analyse.lignesIgnorees);
        analyse.numerosEnDouble(acceptees);
        analyse.inscriptionsEnDouble(acceptees);
        analyse.lenteurIdentification();
        analyse.clotureTresor(posts);
        analyse.refusAge(posts);
        analyse.listeInscrits();
        analyse.rechercheSuspecte();
    }
}
