package fr.zeffut.anonymouspotion.telemetry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Client PostHog : envoie des events vers la Capture API. Async, fire-and-forget,
 * toutes les erreurs avalées — n'impacte jamais le serveur. Opt-out via {@code enabled}.
 */
public final class PostHogClient {

    public static final String API_KEY = "phc_zdMj4p5wo8EvfVApjb2EbfUHJ76zgYGM5wAGz5YJC359";
    public static final String DEFAULT_HOST = "https://eu.i.posthog.com";
    /** Slug de l'application, joint à chaque event pour segmenter un projet PostHog partagé. */
    public static final String APP = "anonymouspotion";

    /** Délai d'établissement de connexion. Sans lui, un pare-feu qui jette le trafic sortant
     * laisse la connexion pendante jusqu'au timeout TCP du système. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Délai de la requête entière, une fois la connexion établie. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Couture de test : implémentation réseau réelle ou mock. */
    public interface Sender { void send(String jsonBody); }

    private final boolean enabled;
    private final String source;
    private final String mcVersion;
    private final String componentVersion;
    private final Sender sender;

    /** Constructeur de prod : envoi HTTP async. */
    public PostHogClient(boolean enabled, String host, String source,
                         String mcVersion, String componentVersion) {
        this(enabled, host, source, mcVersion, componentVersion, httpSender(host));
    }

    /** Constructeur testable : {@code sender} injecté. */
    public PostHogClient(boolean enabled, String host, String source,
                         String mcVersion, String componentVersion, Sender sender) {
        this.enabled = enabled;
        this.source = source;
        this.mcVersion = mcVersion;
        this.componentVersion = componentVersion;
        this.sender = sender;
    }

    public void capture(String event, String distinctId, Map<String, Object> properties) {
        if (!enabled) return;
        try {
            sender.send(buildBody(event, distinctId, properties));
        } catch (Throwable ignored) {
            // fire-and-forget : jamais d'impact sur l'appelant
        }
    }

    String buildBody(String event, String distinctId, Map<String, Object> properties) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("app", APP);
        props.put("source", source);
        props.put("mc_version", mcVersion);
        props.put("component_version", componentVersion);
        if (properties != null) props.putAll(properties);

        // Sans cette ligne, la divulgation du plugin serait fausse. Le code n'envoie aucune IP,
        // mais la Capture API renseigne elle-même $ip à partir de l'IP source de la requête HTTP
        // et en dérive les propriétés $geoip_*, persistées sur l'event ET sur le profil rattaché
        // au distinct_id : l'IP publique du serveur et sa géolocalisation finiraient stockées
        // chez un tiers. La valeur null — le littéral JSON, pas la chaîne "null" — est la consigne
        // que PostHog reconnaît pour ne capturer ni l'IP ni la géolocalisation. Posée après le
        // putAll pour qu'aucune propriété métier ne puisse structurellement l'écraser.
        props.put("$ip", null);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("api_key", API_KEY);
        root.put("event", event);
        root.put("distinct_id", distinctId);
        root.put("timestamp", Instant.now().toString());
        root.put("properties", props);
        return rootJson(root, props);
    }

    /** Sérialise root en injectant l'objet properties déjà construit. */
    private static String rootJson(Map<String, Object> root, Map<String, Object> props) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : root.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(JsonWriter.value(e.getKey())).append(':');
            if ("properties".equals(e.getKey())) {
                sb.append(JsonWriter.write(props));
            } else {
                sb.append(JsonWriter.value(e.getValue()));
            }
        }
        return sb.append('}').toString();
    }

    private static final Executor EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "anonymouspotion-posthog");
                t.setDaemon(true);
                return t;
            });

    private static Sender httpSender(String host) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(EXECUTOR)
                .build();
        String url = host.replaceAll("/+$", "") + "/i/v0/e/";
        return body -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        };
    }
}
