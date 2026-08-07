package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TelemetryTest {

    /**
     * Propriétés jointes par le client à 100 % du trafic. Répétées ici plutôt que partagées avec
     * {@code PostHogClientTest} : deux énoncés indépendants de la même divulgation valent mieux
     * qu'un seul, dont on pourrait relâcher la valeur sans s'en apercevoir.
     */
    private static final Set<String> PROPRIETES_COMMUNES =
            Set.of("app", "source", "mc_version", "component_version", "$ip");

    /** Les clés de l'enveloppe de la Capture API. */
    private static final Set<String> CLES_ENVELOPPE =
            Set.of("api_key", "event", "distinct_id", "timestamp", "properties");

    /** L'ensemble exact attendu : les propriétés communes plus celles, métier, énumérées ici. */
    private static Set<String> avec(String... metier) {
        Set<String> attendues = new HashSet<>(PROPRIETES_COMMUNES);
        attendues.addAll(List.of(metier));
        return attendues;
    }

    /**
     * Les 103 clés {@code death.*} réelles extraites de
     * {@code assets/minecraft/lang/en_us.json} du jar client Minecraft 1.21.11 (dossier
     * {@code fabric-loom} local, {@code minecraft-client.jar}). 33 d'entre elles contiennent du
     * camelCase (ex. {@code death.attack.onFire}) : la garde doit toutes les laisser passer.
     */
    private static final String[] VRAIES_CLES_DEATH_1_21_11 = {
        "death.attack.anvil", "death.attack.anvil.player", "death.attack.arrow",
        "death.attack.arrow.item", "death.attack.badRespawnPoint.link",
        "death.attack.badRespawnPoint.message", "death.attack.cactus",
        "death.attack.cactus.player", "death.attack.cramming", "death.attack.cramming.player",
        "death.attack.dragonBreath", "death.attack.dragonBreath.player", "death.attack.drown",
        "death.attack.drown.player", "death.attack.dryout", "death.attack.dryout.player",
        "death.attack.even_more_magic", "death.attack.explosion",
        "death.attack.explosion.player", "death.attack.explosion.player.item",
        "death.attack.fall", "death.attack.fall.player", "death.attack.fallingBlock",
        "death.attack.fallingBlock.player", "death.attack.fallingStalactite",
        "death.attack.fallingStalactite.player", "death.attack.fireball",
        "death.attack.fireball.item", "death.attack.fireworks",
        "death.attack.fireworks.item", "death.attack.fireworks.player",
        "death.attack.flyIntoWall", "death.attack.flyIntoWall.player", "death.attack.freeze",
        "death.attack.freeze.player", "death.attack.generic", "death.attack.generic.player",
        "death.attack.genericKill", "death.attack.genericKill.player",
        "death.attack.hotFloor", "death.attack.hotFloor.player", "death.attack.inFire",
        "death.attack.inFire.player", "death.attack.inWall", "death.attack.inWall.player",
        "death.attack.indirectMagic", "death.attack.indirectMagic.item", "death.attack.lava",
        "death.attack.lava.player", "death.attack.lightningBolt",
        "death.attack.lightningBolt.player", "death.attack.mace_smash",
        "death.attack.mace_smash.item", "death.attack.magic", "death.attack.magic.player",
        "death.attack.message_too_long", "death.attack.mob", "death.attack.mob.item",
        "death.attack.onFire", "death.attack.onFire.item", "death.attack.onFire.player",
        "death.attack.outOfWorld", "death.attack.outOfWorld.player",
        "death.attack.outsideBorder", "death.attack.outsideBorder.player",
        "death.attack.player", "death.attack.player.item", "death.attack.sonic_boom",
        "death.attack.sonic_boom.item", "death.attack.sonic_boom.player",
        "death.attack.spear", "death.attack.spear.item", "death.attack.stalagmite",
        "death.attack.stalagmite.player", "death.attack.starve", "death.attack.starve.player",
        "death.attack.sting", "death.attack.sting.item", "death.attack.sting.player",
        "death.attack.sweetBerryBush", "death.attack.sweetBerryBush.player",
        "death.attack.thorns", "death.attack.thorns.item", "death.attack.thrown",
        "death.attack.thrown.item", "death.attack.trident", "death.attack.trident.item",
        "death.attack.wither", "death.attack.wither.player", "death.attack.witherSkull",
        "death.attack.witherSkull.item", "death.fell.accident.generic",
        "death.fell.accident.ladder", "death.fell.accident.other_climbable",
        "death.fell.accident.scaffolding", "death.fell.accident.twisting_vines",
        "death.fell.accident.vines", "death.fell.accident.weeping_vines", "death.fell.assist",
        "death.fell.assist.item", "death.fell.finish", "death.fell.finish.item",
        "death.fell.killer",
    };

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
        telemetry().pluginEnabled("1.21.11-132", 8, true, false, true);

        assertTrue(dernier().contains("\"event\":\"plugin_enabled\""));
        assertTrue(dernier().contains("\"server_version\":\"1.21.11-132\""));
        assertTrue(dernier().contains("\"obfuscated_length\":8"));
        assertTrue(dernier().contains("\"obfuscate_weapon_name\":true"));
        assertTrue(dernier().contains("\"log_real_names\":false"));
        assertTrue(dernier().contains("\"online_mode\":true"));
    }

    @Test
    void nEmetQueLesProprietesDeclareesAuDemarrage() {
        telemetry().pluginEnabled("1.21.11-132", 8, true, true, true);

        assertEquals(avec("server_version", "obfuscated_length", "obfuscate_weapon_name",
                "log_real_names", "online_mode"), JsonProbe.propertyKeys(dernier()));
    }

    /**
     * Les deux booléens sont volontairement distincts : passer {@code true, true} laisserait
     * l'assertion verte même si l'implémentation les intervertissait.
     */
    @Test
    void emetUneMortBrouillee() {
        telemetry().deathObfuscated(2, true, false, "death.attack.player");

        assertTrue(dernier().contains("\"event\":\"death_obfuscated\""));
        assertTrue(dernier().contains("\"obfuscated_count\":2"));
        assertTrue(dernier().contains("\"weapon_obfuscated\":true"));
        assertTrue(dernier().contains("\"offline_killer\":false"));
        assertTrue(dernier().contains("\"death_key\":\"death.attack.player\""));
    }

    /** La même vérification dans l'autre sens : une inversion échoue des deux côtés. */
    @Test
    void emetUneMortBrouilleeSansArmeParUnTueurHorsLigne() {
        telemetry().deathObfuscated(1, false, true, "death.attack.player");

        assertTrue(dernier().contains("\"weapon_obfuscated\":false"));
        assertTrue(dernier().contains("\"offline_killer\":true"));
    }

    @Test
    void nEmetQueLesProprietesDeclareesPourUneMort() {
        telemetry().deathObfuscated(2, true, false, "death.attack.player");

        assertEquals(avec("obfuscated_count", "weapon_obfuscated", "offline_killer", "death_key"),
                JsonProbe.propertyKeys(dernier()));
    }

    @Test
    void emetUnEchecDeBrouillage() {
        telemetry().obfuscationFailed(new IllegalStateException("boum"));

        assertTrue(dernier().contains("\"event\":\"obfuscation_failed\""));
        assertTrue(dernier().contains("\"error_type\":\"IllegalStateException\""));
        assertEquals(avec("error_type"), JsonProbe.propertyKeys(dernier()));
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
        assertEquals(avec("subcommand"), JsonProbe.propertyKeys(dernier()));
    }

    @Test
    void emetUnBattementDeSession() {
        telemetry().sessionHeartbeat(30, 7);

        assertTrue(dernier().contains("\"event\":\"session_heartbeat\""));
        assertTrue(dernier().contains("\"uptime_minutes\":30"));
        assertTrue(dernier().contains("\"deaths_obfuscated\":7"));
        assertEquals(avec("uptime_minutes", "deaths_obfuscated"), JsonProbe.propertyKeys(dernier()));
    }

    /** Émet les cinq événements du plugin, pour les vérifications transversales ci-dessous. */
    private void emetLesCinqEvenements() {
        Telemetry t = telemetry();
        t.pluginEnabled("1.21.11-132", 8, true, true, true);
        t.deathObfuscated(1, false, false, "death.attack.player");
        t.obfuscationFailed(new IllegalStateException("boum"));
        t.commandUsed("reload");
        t.sessionHeartbeat(30, 7);
        assertEquals(5, envoyes.size());
    }

    @Test
    void identifieToujoursLeServeurEtJamaisUnJoueur() {
        emetLesCinqEvenements();

        for (String body : envoyes) {
            // L'ensemble exact remplace la recherche de littéraux choisis d'avance : c'est la
            // seule forme d'assertion qui puisse échouer sur une propriété inattendue dont on
            // n'aurait, par construction, pas deviné le nom.
            assertEquals(CLES_ENVELOPPE, JsonProbe.envelopeKeys(body));
            assertTrue(body.contains("\"distinct_id\":\"install-42\""));
        }
    }

    @Test
    void demandeAPostHogDeNeCapturerNiIpNiGeolocalisationSurChaqueEvenement() {
        emetLesCinqEvenements();

        for (String body : envoyes) {
            assertTrue(body.contains("\"$ip\":null"), body);
            assertFalse(body.contains("\"$ip\":\"null\""), body);
        }
    }

    @Test
    void laisseIntacteUneCleDeTraductionValide() {
        telemetry().deathObfuscated(1, false, false, "death.attack.lava.player");

        assertTrue(dernier().contains("\"death_key\":\"death.attack.lava.player\""));
    }

    @Test
    void laisseIntactesLesVraiesClesVanillaEnCamelCase() {
        telemetry().deathObfuscated(1, false, false, "death.attack.onFire");
        assertTrue(dernier().contains("\"death_key\":\"death.attack.onFire\""));

        telemetry().deathObfuscated(1, false, false, "death.attack.lightningBolt");
        assertTrue(dernier().contains("\"death_key\":\"death.attack.lightningBolt\""));

        telemetry().deathObfuscated(1, false, false, "death.attack.fallingBlock");
        assertTrue(dernier().contains("\"death_key\":\"death.attack.fallingBlock\""));
    }

    @Test
    void laisseIntactesLes103VraiesClesDeathDuJarClient1_21_11() {
        for (String cle : VRAIES_CLES_DEATH_1_21_11) {
            telemetry().deathObfuscated(1, false, false, cle);

            assertTrue(dernier().contains("\"death_key\":\"" + cle + "\""), cle);
        }
    }

    @Test
    void laisseIntactUnknown() {
        telemetry().deathObfuscated(1, false, false, "unknown");

        assertTrue(dernier().contains("\"death_key\":\"unknown\""));
    }

    @Test
    void rejetteUnPseudoBrutSansEspace() {
        telemetry().deathObfuscated(1, false, false, "Steve_99");

        assertTrue(dernier().contains("\"death_key\":\"invalid\""));
        assertFalse(dernier().contains("Steve_99"));
    }

    @Test
    void remplaceUnMessageDeMortRenduParInvalid() {
        telemetry().deathObfuscated(1, false, false, "Zeffut a été tué par Steve");

        assertTrue(dernier().contains("\"death_key\":\"invalid\""));
        assertFalse(dernier().contains("Zeffut"));
        assertFalse(dernier().contains("Steve"));
    }

    @Test
    void remplaceUnPseudoBrutQuiEtaitAccepteParLAncienneRegexParInvalid() {
        telemetry().deathObfuscated(1, false, false, "Zeffut");

        assertTrue(dernier().contains("\"death_key\":\"invalid\""));
        assertFalse(dernier().contains("\"death_key\":\"Zeffut\""));
    }

    @Test
    void remplaceUneCleNulleParInvalidSansLeverDException() {
        telemetry().deathObfuscated(1, false, false, null);

        assertTrue(dernier().contains("\"death_key\":\"invalid\""));
    }

    /**
     * Le préfixe {@code death.} ne suffit pas : un message rendu commençant par une clé
     * — ce que produirait un plugin tiers qui préfixe ses messages — porte un pseudo. Seul le
     * préfixe absent était testé jusqu'ici, ce qui laissait ce chemin sans couverture.
     */
    @Test
    void remplaceParInvalidUneCleAuBonPrefixeMaisAuMauvaisFormat() {
        telemetry().deathObfuscated(1, false, false, "death.attack.player: Zeffut");

        assertTrue(dernier().contains("\"death_key\":\"invalid\""));
        assertFalse(dernier().contains("Zeffut"));
    }

    @Test
    void remplaceParInvalidUneCleAuBonFormatMaisTropLongue() {
        // 6 + 59 = 65 caractères, un de plus que le plafond, et sans aucun caractère interdit :
        // seule la longueur peut la faire rejeter.
        String tropLongue = "death." + "a".repeat(59);

        telemetry().deathObfuscated(1, false, false, tropLongue);

        assertTrue(dernier().contains("\"death_key\":\"invalid\""));
        assertFalse(dernier().contains(tropLongue));
    }

    @Test
    void laisseIntacteUneCleExactementALaLongueurMaximale() {
        String limite = "death." + "a".repeat(58);
        assertEquals(64, limite.length());

        telemetry().deathObfuscated(1, false, false, limite);

        assertTrue(dernier().contains("\"death_key\":\"" + limite + "\""));
    }
}
