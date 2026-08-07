package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryTest {

    private final List<String> envoyes = new ArrayList<>();

    private Telemetry telemetry() {
        PostHogClient client = new PostHogClient(true, "https://example.invalid", "paper",
                "1.21.11", "1.0.0", envoyes::add);
        return new Telemetry(client, "install-42");
    }

    private String dernier() {
        return envoyes.get(envoyes.size() - 1);
    }

    @Test
    void emetLeDemarrageAvecLaConfiguration() {
        telemetry().pluginEnabled("1.21.11-132", 8, true, true, true);

        assertTrue(dernier().contains("\"event\":\"plugin_enabled\""));
        assertTrue(dernier().contains("\"obfuscated_length\":8"));
        assertTrue(dernier().contains("\"obfuscate_weapon_name\":true"));
    }

    @Test
    void emetUneMortBrouillee() {
        telemetry().deathObfuscated(2, true, false, "death.attack.player");

        assertTrue(dernier().contains("\"event\":\"death_obfuscated\""));
        assertTrue(dernier().contains("\"obfuscated_count\":2"));
        assertTrue(dernier().contains("\"death_key\":\"death.attack.player\""));
    }

    @Test
    void emetUnEchecDeBrouillage() {
        telemetry().obfuscationFailed(new IllegalStateException("boum"));

        assertTrue(dernier().contains("\"event\":\"obfuscation_failed\""));
        assertTrue(dernier().contains("\"error_type\":\"IllegalStateException\""));
    }

    @Test
    void nEmetPasLeMessageDeLException() {
        telemetry().obfuscationFailed(new IllegalStateException("Zeffut a été tué par Steve"));

        assertFalse(dernier().contains("Zeffut"));
        assertFalse(dernier().contains("Steve"));
    }

    @Test
    void emetLUsageDUneCommande() {
        telemetry().commandUsed("reload");

        assertTrue(dernier().contains("\"event\":\"command_used\""));
        assertTrue(dernier().contains("\"subcommand\":\"reload\""));
    }

    @Test
    void emetUnBattementDeSession() {
        telemetry().sessionHeartbeat(30, 7);

        assertTrue(dernier().contains("\"event\":\"session_heartbeat\""));
        assertTrue(dernier().contains("\"uptime_minutes\":30"));
        assertTrue(dernier().contains("\"deaths_obfuscated\":7"));
    }

    @Test
    void identifieToujoursLeServeurEtJamaisUnJoueur() {
        Telemetry t = telemetry();
        t.pluginEnabled("1.21.11-132", 8, true, true, true);
        t.deathObfuscated(1, false, false, "death.attack.player");
        t.commandUsed("reload");

        for (String body : envoyes) {
            assertTrue(body.contains("\"distinct_id\":\"install-42\""));
            assertFalse(body.contains("username"));
            assertFalse(body.contains("player_ip"));
        }
    }
}
