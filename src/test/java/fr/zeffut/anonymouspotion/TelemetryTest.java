package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.zeffut.anonymouspotion.telemetry.PostHogClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryTest {

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
        t.obfuscationFailed(new IllegalStateException("boum"));
        t.commandUsed("reload");
        t.sessionHeartbeat(30, 7);

        assertEquals(5, envoyes.size());
        for (String body : envoyes) {
            assertTrue(body.contains("\"distinct_id\":\"install-42\""));
            assertFalse(body.contains("username"));
            assertFalse(body.contains("player_ip"));
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
}
