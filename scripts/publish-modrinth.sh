#!/usr/bin/env bash
# Crée le projet Modrinth AnonymousPotion et y publie une version.
#
# Prérequis : MODRINTH_TOKEN dans ~/.claude/secrets.env, avec les scopes
# Create projects / Write projects / Create versions / Write versions.
#
# Le projet est créé en BROUILLON : il n'est pas soumis à la revue Modrinth.
# Ajouter le logo puis soumettre depuis l'interface.
#
# Usage : ./scripts/publish-modrinth.sh [version]      (défaut : 1.0.0)

set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-1.0.0}"
JAR="build/libs/AnonymousPotion.jar"
SLUG="anonymouspotion"
# Seule version réellement testée. Élargir ici après vérification sur d'autres 1.21.x.
GAME_VERSIONS='["1.21.11"]'

[ -f "$JAR" ] || { echo "Jar absent — lancer ./gradlew build" >&2; exit 1; }

# shellcheck disable=SC1090
source ~/.claude/secrets.env
: "${MODRINTH_TOKEN:?MODRINTH_TOKEN absent de ~/.claude/secrets.env}"

API="https://api.modrinth.com/v2"
BODY=$(python3 -c 'import json,sys; print(json.dumps(open("docs/modrinth-description.md").read()))')

# ---------- 1. Le projet, s'il n'existe pas déjà ----------
if curl -sf -o /dev/null "$API/project/$SLUG"; then
    echo "Projet $SLUG déjà présent, on passe à la version."
else
    echo "Création du projet $SLUG…"
    cat > /tmp/ap-project.json <<JSON
{
  "slug": "$SLUG",
  "title": "AnonymousPotion",
  "description": "Players under Invisibility never have their name shown in death messages — unless they are the victim",
  "body": $BODY,
  "categories": ["game-mechanics", "social", "utility"],
  "client_side": "unsupported",
  "server_side": "required",
  "license_id": "MIT",
  "project_type": "mod",
  "is_draft": true,
  "initial_versions": [],
  "source_url": "https://github.com/Zeffut/AnonymousPotion"
}
JSON
    curl -sS -X POST "$API/project" \
        -H "Authorization: $MODRINTH_TOKEN" \
        -F "data=@/tmp/ap-project.json;type=application/json" \
        | python3 -c 'import json,sys; d=json.load(sys.stdin); print("  ->", d.get("id","ERREUR:"+json.dumps(d)))'
    rm -f /tmp/ap-project.json
fi

# ---------- 2. La version ----------
echo "Publication de la version $VERSION…"
cat > /tmp/ap-version.json <<JSON
{
  "name": "AnonymousPotion $VERSION",
  "version_number": "$VERSION",
  "changelog": "Première version.\n\nBrouille le pseudo des joueurs invisibles dans les messages de mort, sauf quand le joueur invisible est lui-même la victime.",
  "dependencies": [],
  "game_versions": $GAME_VERSIONS,
  "version_type": "release",
  "loaders": ["paper", "purpur", "folia"],
  "featured": true,
  "project_id": "$SLUG",
  "file_parts": ["file"],
  "primary_file": "file"
}
JSON

curl -sS -X POST "$API/version" \
    -H "Authorization: $MODRINTH_TOKEN" \
    -F "data=@/tmp/ap-version.json;type=application/json" \
    -F "file=@$JAR;type=application/java-archive" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print("  ->", d.get("id","ERREUR:"+json.dumps(d)))'
rm -f /tmp/ap-version.json

echo
echo "Terminé. Brouillon : https://modrinth.com/mod/$SLUG"
echo "Ajouter le logo, puis soumettre à la revue depuis l'interface."
