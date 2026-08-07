package fr.zeffut.anonymouspotion;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sonde de test : extrait les clés d'un corps JSON produit par le client PostHog.
 *
 * <p>Existe parce qu'un test « anti-fuite » qui vérifie l'absence de deux littéraux choisis
 * d'avance ne peut structurellement pas détecter une propriété inattendue portant un autre nom :
 * il passe au vert quoi qu'on ajoute au corps. Comparer un ensemble exact de clés, si — ce qui
 * en fait le seul test capable de tenir la promesse « rien d'autre n'est transmis ».
 */
public final class JsonProbe {

    private JsonProbe() {}

    /** Clés de l'objet racine du corps : {@code api_key}, {@code event}, etc. */
    public static Set<String> envelopeKeys(String json) {
        return keysAtDepth(json, 1);
    }

    /** Clés de l'objet {@code properties} imbriqué dans la racine. */
    public static Set<String> propertyKeys(String json) {
        return keysAtDepth(json, 2);
    }

    /**
     * Toutes les clés situées à la profondeur demandée, une clé étant un littéral chaîne
     * immédiatement suivi d'un deux-points. Le corps analysé est toujours produit par
     * {@code JsonWriter}, dont la forme est connue : une racine plate contenant un unique objet
     * {@code properties}, plat lui aussi.
     */
    private static Set<String> keysAtDepth(String json, int wanted) {
        Set<String> keys = new LinkedHashSet<>();
        int depth = 0;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                i++;
            } else if (c == '}' || c == ']') {
                depth--;
                i++;
            } else if (c == '"') {
                StringBuilder token = new StringBuilder();
                i++;
                while (i < json.length() && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        token.append(json.charAt(i + 1));
                        i += 2;
                    } else {
                        token.append(json.charAt(i));
                        i++;
                    }
                }
                i++;
                if (i < json.length() && json.charAt(i) == ':' && depth == wanted) {
                    keys.add(token.toString());
                }
            } else {
                i++;
            }
        }
        return keys;
    }
}
