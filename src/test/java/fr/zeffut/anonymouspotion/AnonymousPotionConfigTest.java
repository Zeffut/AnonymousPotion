package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnonymousPotionConfigTest {

    private final List<String> warnings = new ArrayList<>();

    private AnonymousPotionConfig load(int length, String filler) {
        return AnonymousPotionConfig.of(length, filler, true, true, warnings::add);
    }

    @Test
    void accepteDesValeursValides() {
        AnonymousPotionConfig config = AnonymousPotionConfig.of(12, "x", false, false, warnings::add);

        assertEquals(12, config.obfuscatedLength());
        assertEquals('x', config.fillerCharacter());
        assertFalse(config.obfuscateWeaponName());
        assertFalse(config.logRealNames());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void ramoneUneLongueurTropPetiteAuDefaut() {
        AnonymousPotionConfig config = load(0, "a");

        assertEquals(AnonymousPotionConfig.DEFAULT_LENGTH, config.obfuscatedLength());
        assertEquals(1, warnings.size());
    }

    @Test
    void ramoneUneLongueurTropGrandeAuDefaut() {
        AnonymousPotionConfig config = load(500, "a");

        assertEquals(AnonymousPotionConfig.DEFAULT_LENGTH, config.obfuscatedLength());
        assertEquals(1, warnings.size());
    }

    @Test
    void accepteLesBornesDeLongueur() {
        assertEquals(AnonymousPotionConfig.MIN_LENGTH, load(AnonymousPotionConfig.MIN_LENGTH, "a").obfuscatedLength());
        assertEquals(AnonymousPotionConfig.MAX_LENGTH, load(AnonymousPotionConfig.MAX_LENGTH, "a").obfuscatedLength());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void refuseUnRemplissageDePlusieursCaracteres() {
        // « xyz » et non « abc » : « abc » commence par le caractère par défaut, l'assertion
        // passerait donc aussi avec un charAt(0) qui ne contrôlerait pas la longueur.
        AnonymousPotionConfig config = load(8, "xyz");

        assertEquals(AnonymousPotionConfig.DEFAULT_FILLER, config.fillerCharacter());
        assertEquals(1, warnings.size());
    }

    @Test
    void refuseUnRemplissageVide() {
        AnonymousPotionConfig config = load(8, "");

        assertEquals(AnonymousPotionConfig.DEFAULT_FILLER, config.fillerCharacter());
        assertEquals(1, warnings.size());
    }

    @Test
    void refuseUnRemplissageAbsent() {
        AnonymousPotionConfig config = load(8, null);

        assertEquals(AnonymousPotionConfig.DEFAULT_FILLER, config.fillerCharacter());
        assertEquals(1, warnings.size());
    }

    @Test
    void signaleChaqueProblemeSeparement() {
        load(0, "abc");

        assertEquals(2, warnings.size());
    }
}
