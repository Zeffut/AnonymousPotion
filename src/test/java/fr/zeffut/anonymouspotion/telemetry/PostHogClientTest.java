package fr.zeffut.anonymouspotion.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.zeffut.anonymouspotion.JsonProbe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostHogClientTest {

    /**
     * Les propriétés que le client joint lui-même à 100 % du trafic, quelle que soit l'événement.
     * Énumérées ici en toutes lettres : c'est la divulgation du README qui est vérifiée, donc
     * l'ensemble doit être exact — toute propriété ajoutée au corps sans être documentée fait
     * échouer les tests qui comparent à cet ensemble.
     */
    private static final Set<String> PROPRIETES_COMMUNES =
            Set.of("app", "source", "mc_version", "component_version", "$ip");

    /** Les clés de l'enveloppe de la Capture API, elles aussi exhaustives. */
    private static final Set<String> CLES_ENVELOPPE =
            Set.of("api_key", "event", "distinct_id", "timestamp", "properties");

    private final List<String> envoyes = new ArrayList<>();

    private PostHogClient client(boolean enabled) {
        return new PostHogClient(enabled, "https://example.invalid", "paper", "1.21.11", "1.0.0",
                envoyes::add);
    }

    @Test
    void envoieUnEventQuandActive() {
        client(true).capture("plugin_enabled", "install-42", Map.of());

        assertEquals(1, envoyes.size());
        assertTrue(envoyes.get(0).contains("\"event\":\"plugin_enabled\""));
        assertTrue(envoyes.get(0).contains("\"distinct_id\":\"install-42\""));
    }

    @Test
    void nEnvoieRienQuandDesactive() {
        client(false).capture("plugin_enabled", "install-42", Map.of());

        assertTrue(envoyes.isEmpty());
    }

    @Test
    void marqueChaqueEventAvecLeSlugDeLApplication() {
        String body = client(true).buildBody("plugin_enabled", "install-42", Map.of());

        assertTrue(body.contains("\"app\":\"anonymouspotion\""));
    }

    @Test
    void jointLesProprietesCommunes() {
        String body = client(true).buildBody("plugin_enabled", "install-42", Map.of());

        assertTrue(body.contains("\"source\":\"paper\""));
        assertTrue(body.contains("\"mc_version\":\"1.21.11\""));
        assertTrue(body.contains("\"component_version\":\"1.0.0\""));
    }

    @Test
    void serialiseLesProprietesMetier() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("obfuscated_count", 2);
        props.put("weapon_obfuscated", true);

        String body = client(true).buildBody("death_obfuscated", "install-42", props);

        assertTrue(body.contains("\"obfuscated_count\":2"));
        assertTrue(body.contains("\"weapon_obfuscated\":true"));
    }

    @Test
    void echappeLesGuillemetsDansLesValeurs() {
        String body = client(true).buildBody("x", "install-42", Map.of("k", "a\"b"));

        assertTrue(body.contains("a\\\"b"));
    }

    @Test
    void nePropageJamaisUneErreurDEnvoi() {
        PostHogClient quiCasse = new PostHogClient(true, "h", "paper", "1.21.11", "1.0.0",
                body -> { throw new IllegalStateException("réseau coupé"); });

        quiCasse.capture("plugin_enabled", "install-42", Map.of());
        // Aucune exception ne doit remonter : le test passe s'il arrive ici.
    }

    @Test
    void nEnvoieExactementQueLesProprietesCommunesQuandAucuneProprieteMetier() {
        String body = client(true).buildBody("death_obfuscated", "install-42", Map.of());

        assertEquals(PROPRIETES_COMMUNES, JsonProbe.propertyKeys(body));
    }

    @Test
    void nAjouteRienAuxProprietesMetierFournies() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("obfuscated_count", 2);
        props.put("death_key", "death.attack.player");

        String body = client(true).buildBody("death_obfuscated", "install-42", props);

        Set<String> attendues = new java.util.HashSet<>(PROPRIETES_COMMUNES);
        attendues.add("obfuscated_count");
        attendues.add("death_key");
        assertEquals(attendues, JsonProbe.propertyKeys(body));
    }

    @Test
    void nEnvoieExactementQueLesClesDEnveloppeAttendues() {
        String body = client(true).buildBody("death_obfuscated", "install-42", Map.of());

        assertEquals(CLES_ENVELOPPE, JsonProbe.envelopeKeys(body));
    }

    /**
     * Le code n'envoie aucune IP, mais la Capture API renseigne {@code $ip} depuis l'IP source
     * de la requête HTTP et en dérive les {@code $geoip_*}, persistées sur l'event et sur le
     * profil. {@code "$ip":null} est la consigne qui coupe cette capture ; sans elle, la
     * promesse « aucune adresse IP n'est transmise » du README serait fausse.
     */
    @Test
    void demandeAPostHogDeNeCapturerNiIpNiGeolocalisation() {
        String body = client(true).buildBody("death_obfuscated", "install-42", Map.of());

        assertTrue(body.contains("\"$ip\":null"), body);
        // La chaîne "null" serait une valeur d'IP quelconque pour PostHog, donc sans effet.
        assertFalse(body.contains("\"$ip\":\"null\""), body);
    }

    /** Une propriété métier ne doit pas pouvoir écraser la consigne, même en portant son nom. */
    @Test
    void neLaissePasUneProprieteMetierEcraserLaConsigneIp() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("$ip", "203.0.113.7");

        String body = client(true).buildBody("death_obfuscated", "install-42", props);

        assertTrue(body.contains("\"$ip\":null"), body);
        assertFalse(body.contains("203.0.113.7"), body);
    }
}
