package fr.zeffut.anonymouspotion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mémoire courte des joueurs déconnectés alors qu'ils étaient invisibles.
 *
 * <p>Un joueur invisible peut frapper sa cible puis se déconnecter : la cible meurt quelques
 * secondes plus tard de chute, de lave ou de poison, et le message de mort cite toujours
 * l'attaquant, nœud {@code SHOW_ENTITY} intact. Mais {@code Bukkit.getPlayer(uuid)} retourne
 * {@code null}, l'attaquant passe pour visible et son pseudo part en clair : le plugin échoue
 * ouvert là où il doit échouer fermé. Cette mémoire bouche ce trou.
 *
 * <p>L'horloge est passée en paramètre plutôt que lue en interne : c'est ce qui rend la
 * fenêtre vérifiable en JUnit sans attendre réellement une minute.
 *
 * <p>Classe volontairement sans dépendance Bukkit, et non thread-safe : elle n'est appelée que
 * depuis le thread principal, où le serveur distribue les événements de déconnexion et de mort.
 */
public final class InvisibilityMemory {

    /**
     * Durée pendant laquelle un joueur déconnecté reste traité comme invisible. Une minute
     * couvre largement les morts différées (chute, lave, poison, noyade) sans retenir un
     * joueur parti depuis longtemps.
     */
    public static final long WINDOW_MILLIS = 60_000L;

    private final Map<UUID, Long> quitInstants = new HashMap<>();

    /** Retient qu'un joueur invisible vient de se déconnecter à l'instant {@code nowMillis}. */
    public void remember(UUID playerId, long nowMillis) {
        purge(nowMillis);
        quitInstants.put(playerId, nowMillis);
    }

    /**
     * Vrai si ce joueur s'est déconnecté invisible il y a moins de {@link #WINDOW_MILLIS}
     * millisecondes.
     */
    public boolean wasRecentlyInvisible(UUID playerId, long nowMillis) {
        purge(nowMillis);
        return quitInstants.containsKey(playerId);
    }

    /** Nombre d'entrées retenues. Visible pour les tests : la purge doit être observable. */
    int size() {
        return quitInstants.size();
    }

    /**
     * Retire les entrées sorties de la fenêtre. Sans elle, la table grossirait tant que le
     * serveur tourne : rien d'autre ne supprime jamais un UUID mémorisé.
     */
    private void purge(long nowMillis) {
        quitInstants.values().removeIf(quitInstant -> nowMillis - quitInstant >= WINDOW_MILLIS);
    }
}
