package fr.zeffut.anonymouspotion;

import java.util.function.Consumer;

/**
 * Réglages du plugin, déjà validés. Ne dépend pas de Bukkit : le plugin lit le YAML et
 * passe ici des valeurs brutes, ce qui rend la validation testable sans serveur.
 */
public record AnonymousPotionConfig(
        int obfuscatedLength,
        char fillerCharacter,
        boolean obfuscateWeaponName,
        boolean logRealNames) {

    public static final int DEFAULT_LENGTH = 8;
    public static final char DEFAULT_FILLER = 'a';
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 32;

    /**
     * Construit une configuration en corrigeant les valeurs invalides. Chaque correction est
     * signalée à {@code warnings} : une valeur douteuse ne doit jamais empêcher le démarrage.
     */
    public static AnonymousPotionConfig of(
            int obfuscatedLength,
            String fillerCharacter,
            boolean obfuscateWeaponName,
            boolean logRealNames,
            Consumer<String> warnings) {

        int length = obfuscatedLength;
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            warnings.accept("obfuscated-length doit être entre " + MIN_LENGTH + " et " + MAX_LENGTH
                    + " (valeur lue : " + obfuscatedLength + "). Utilisation de " + DEFAULT_LENGTH + ".");
            length = DEFAULT_LENGTH;
        }

        char filler = DEFAULT_FILLER;
        if (fillerCharacter == null || fillerCharacter.length() != 1) {
            warnings.accept("filler-character doit faire exactement un caractère (valeur lue : "
                    + fillerCharacter + "). Utilisation de '" + DEFAULT_FILLER + "'.");
        } else {
            filler = fillerCharacter.charAt(0);
        }

        return new AnonymousPotionConfig(length, filler, obfuscateWeaponName, logRealNames);
    }
}
