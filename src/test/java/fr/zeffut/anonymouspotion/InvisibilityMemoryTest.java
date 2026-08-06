package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvisibilityMemoryTest {

    private static final UUID PARTI = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Instant de déconnexion arbitraire : l'horloge est injectée, aucune attente réelle. */
    private static final long DECONNEXION = 1_700_000_000_000L;

    private final InvisibilityMemory memory = new InvisibilityMemory();

    @Test
    void seSouvientDansLaFenetre() {
        memory.remember(PARTI, DECONNEXION);

        assertTrue(memory.wasRecentlyInvisible(
                PARTI, DECONNEXION + InvisibilityMemory.WINDOW_MILLIS - 1));
    }

    @Test
    void oublieAuDelaDeLaFenetre() {
        memory.remember(PARTI, DECONNEXION);

        assertFalse(memory.wasRecentlyInvisible(
                PARTI, DECONNEXION + InvisibilityMemory.WINDOW_MILLIS + 1));
    }

    @Test
    void ignoreUnJoueurJamaisMemorise() {
        // Un autre joueur est mémorisé au même instant : l'ensemble n'est pas vide, la réponse
        // négative vient donc bien de la recherche par UUID.
        memory.remember(AUTRE, DECONNEXION);

        assertFalse(memory.wasRecentlyInvisible(PARTI, DECONNEXION));
    }

    @Test
    void purgeLesEntreesExpirees() {
        memory.remember(PARTI, DECONNEXION);
        assertEquals(1, memory.size());

        memory.wasRecentlyInvisible(AUTRE, DECONNEXION + InvisibilityMemory.WINDOW_MILLIS + 1);

        assertEquals(0, memory.size());
    }
}
