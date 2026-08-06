package fr.zeffut.anonymouspotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class NameObfuscatorTest {

    private static final UUID VICTIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KILLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final NameObfuscator obfuscator = new NameObfuscator(8, 'a', true);

    /** Reproduit un nœud « pseudo » tel que le serveur le construit. */
    private static Component playerName(String name, UUID id) {
        return Component.text(name)
                .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), id, Component.text(name)))
                .clickEvent(ClickEvent.suggestCommand("/tell " + name))
                .insertion(name);
    }

    /** Reproduit « Zeffut a été tué par Steve ». */
    private static Component deathMessage() {
        return Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                playerName("Steve", KILLER_ID));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Sérialise un nœud d'argument précis, pour contourner le piège décrit ci-dessous. */
    private static Component argumentOf(Component message, int index) {
        return ((TranslatableComponent) message).arguments().get(index).asComponent();
    }

    /** Reproduit le nœud d'un objet nommé, tel qu'affiché dans « ... avec [Excalibur] ». */
    private static Component namedItem(String itemName) {
        return Component.text()
                .append(Component.text("["))
                .append(Component.text(itemName))
                .append(Component.text("]"))
                .hoverEvent(HoverEvent.showItem(Key.key("minecraft:diamond_sword"), 1))
                .build();
    }

    private static Component deathMessageWithItem(String itemName) {
        return Component.translatable("death.attack.player.item",
                playerName("Zeffut", VICTIM_ID),
                playerName("Steve", KILLER_ID),
                namedItem(itemName));
    }

    // Note d'écart par rapport au brief : PlainTextComponentSerializer ne résout jamais les
    // arguments d'un TranslatableComponent (seul le fallback ou la clé de traduction est rendu,
    // cf. ComponentFlattenerImpl#BASIC dans adventure-api). `plain(result)` sur le message
    // entier renvoie donc toujours "death.attack.player", quel que soit le contenu réel des
    // arguments. Les assertions ci-dessous sérialisent directement le nœud argument concerné,
    // comme le fait déjà `neTouchePasUnMessageSansCiblePresente` dans le brief original.

    @Test
    void remplaceLePseudoCible() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component killerArg = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertFalse(plain(killerArg).contains("Steve"));
        assertTrue(plain(killerArg).contains("aaaaaaaa"));
    }

    @Test
    void laisseIntactLePseudoNonCible() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component victimArg = ((TranslatableComponent) result).arguments().get(0).asComponent();

        assertTrue(plain(victimArg).contains("Zeffut"));
    }

    @Test
    void supprimeHoverClickEtInsertionDuNoeudRemplace() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component replaced = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertNull(replaced.hoverEvent());
        assertNull(replaced.clickEvent());
        assertNull(replaced.insertion());
    }

    @Test
    void appliqueLaDecorationObfusquee() {
        Component result = obfuscator.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component replaced = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertTrue(replaced.hasDecoration(TextDecoration.OBFUSCATED));
    }

    @Test
    void conserveLaCouleurDuNoeudRemplace() {
        Component message = Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                playerName("Steve", KILLER_ID).color(NamedTextColor.RED));

        Component result = obfuscator.obfuscate(message, Set.of(KILLER_ID));
        Component replaced = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertEquals(NamedTextColor.RED, replaced.color());
    }

    @Test
    void utiliseExactementLaLongueurConfiguree() {
        NameObfuscator court = new NameObfuscator(3, 'x', true);

        Component result = court.obfuscate(deathMessage(), Set.of(KILLER_ID));
        Component killerArg = ((TranslatableComponent) result).arguments().get(1).asComponent();

        assertTrue(plain(killerArg).contains("xxx"));
        assertFalse(plain(killerArg).contains("xxxx"));
    }

    @Test
    void descendDansLesEnfants() {
        Component message = Component.text("Le tueur : ").append(playerName("Steve", KILLER_ID));

        Component result = obfuscator.obfuscate(message, Set.of(KILLER_ID));

        assertFalse(plain(result).contains("Steve"));
        assertTrue(plain(result).startsWith("Le tueur : "));
    }

    @Test
    void descendDansLesArgumentsImbriques() {
        Component message = Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                Component.translatable("chat.square_brackets", playerName("Steve", KILLER_ID)));

        Component result = obfuscator.obfuscate(message, Set.of(KILLER_ID));

        // `plain(result)` ne descend jamais dans les arguments (voir note plus haut) : on
        // extrait donc le nœud imbriqué pour vérifier que le parcours l'a bien atteint.
        TranslatableComponent nested =
                (TranslatableComponent) ((TranslatableComponent) result).arguments().get(1).asComponent();
        Component killerArg = nested.arguments().get(0).asComponent();

        assertFalse(plain(killerArg).contains("Steve"));
        assertTrue(plain(killerArg).contains("aaaaaaaa"));
    }

    @Test
    void retourneLeMessageInchangeQuandAucuneCible() {
        Component message = deathMessage();

        assertSame(message, obfuscator.obfuscate(message, Set.of()));
    }

    @Test
    void neTouchePasUnMessageSansCiblePresente() {
        Component message = deathMessage();
        UUID absent = UUID.fromString("00000000-0000-0000-0000-000000000009");

        Component result = obfuscator.obfuscate(message, Set.of(absent));

        assertEquals("Steve", plain(((TranslatableComponent) result).arguments().get(1).asComponent()));
    }

    @Test
    void collecteLesUuidDesEntitesMentionnees() {
        Set<UUID> ids = NameObfuscator.collectEntityIds(deathMessage());

        assertEquals(Set.of(VICTIM_ID, KILLER_ID), ids);
    }

    @Test
    void collecteLesUuidDansLesArgumentsImbriques() {
        Component message = Component.translatable("death.attack.player",
                playerName("Zeffut", VICTIM_ID),
                Component.translatable("chat.square_brackets", playerName("Steve", KILLER_ID)));

        assertEquals(Set.of(VICTIM_ID, KILLER_ID), NameObfuscator.collectEntityIds(message));
    }

    @Test
    void collecteUnEnsembleVideQuandAucuneEntite() {
        assertTrue(NameObfuscator.collectEntityIds(Component.text("Zeffut est tombé de haut")).isEmpty());
    }

    @Test
    void brouilleLeNomDArmeQuandLOptionEstActive() {
        Component result = obfuscator.obfuscate(deathMessageWithItem("Excalibur"), Set.of(KILLER_ID));

        assertEquals("[aaaaaaaa]", plain(argumentOf(result, 2)));
    }

    @Test
    void supprimeLeSurvolDuNoeudDArmeBrouille() {
        Component result = obfuscator.obfuscate(deathMessageWithItem("Excalibur"), Set.of(KILLER_ID));
        Component item = ((TranslatableComponent) result).arguments().get(2).asComponent();

        assertNull(item.hoverEvent());
    }

    @Test
    void laisseLeNomDArmeQuandLOptionEstDesactivee() {
        NameObfuscator sansArme = new NameObfuscator(8, 'a', false);

        Component result = sansArme.obfuscate(deathMessageWithItem("Excalibur"), Set.of(KILLER_ID));

        assertEquals("[Excalibur]", plain(argumentOf(result, 2)));
        assertEquals("aaaaaaaa", plain(argumentOf(result, 1)));
    }

    @Test
    void neBrouillePasLArmeSansJoueurCible() {
        Component message = deathMessageWithItem("Excalibur");

        assertSame(message, obfuscator.obfuscate(message, Set.of()));
    }
}
