#!/usr/bin/env bash
# Crée le projet Modrinth AnonymousPotion et y publie une version.
#
# Prérequis : MODRINTH_TOKEN dans ~/.claude/.mc-secrets.env (source unique,
# cf. ~/.claude/mc-conventions.md).
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

set -a
# shellcheck disable=SC1090
source ~/.claude/.mc-secrets.env
set +a
: "${MODRINTH_TOKEN:?MODRINTH_TOKEN absent de ~/.claude/.mc-secrets.env}"

API="https://api.modrinth.com/v2"

# Les conventions imposent de vérifier la validité du token avant toute publication.
CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: $MODRINTH_TOKEN" "$API/user")
[ "$CODE" = "200" ] || { echo "Token Modrinth invalide (HTTP $CODE)" >&2; exit 1; }

BODY=$(python3 -c 'import json,sys; print(json.dumps(open("docs/modrinth-description.md").read()))')
CHANGELOG_FILE="docs/changelogs/$VERSION.md"
[ -f "$CHANGELOG_FILE" ] || { echo "Changelog absent : $CHANGELOG_FILE" >&2; exit 1; }
CHANGELOG=$(python3 -c "import json;print(json.dumps(open('$CHANGELOG_FILE').read()))")

# ---------- 1. Le projet, s'il n'existe pas déjà ----------
if curl -sf -o /dev/null -H "Authorization: $MODRINTH_TOKEN" "$API/project/$SLUG"; then
    # La fiche évolue avec le plugin : on la resynchronise à chaque publication,
    # sans quoi la description en ligne décrirait une version antérieure.
    echo "Projet $SLUG déjà présent, mise à jour de la description…"
    printf '{"body": %s}' "$BODY" > /tmp/ap-body.json
    curl -sS -X PATCH "$API/project/$SLUG" \
        -H "Authorization: $MODRINTH_TOKEN" \
        -H "Content-Type: application/json" \
        --data @/tmp/ap-body.json -w '  -> HTTP %{http_code}\n' -o /dev/null
    rm -f /tmp/ap-body.json
else
    echo "Création du projet ${SLUG}…"
    cat > /tmp/ap-project.json <<JSON
{
  "slug": "$SLUG",
  "title": "AnonymousPotion",
  "description": "Players under Invisibility never have their name shown in death messages — unless they are the victim",
  "body": $BODY,
  "categories": ["game-mechanics", "social", "utility"],
  "client_side": "unsupported",
  "server_side": "required",
  "license_id": "LicenseRef-PolyForm-Noncommercial-1.0.0",
  "license_url": "https://polyformproject.org/licenses/noncommercial/1.0.0",
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
# project_id attend l'ID base62 du projet, pas son slug : un slug provoque
# « Base62 decoding overflowed ».
PROJECT_ID=$(curl -sS -H "Authorization: $MODRINTH_TOKEN" "$API/project/$SLUG" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
echo "Publication de la version ${VERSION} sur ${PROJECT_ID}…"
cat > /tmp/ap-version.json <<JSON
{
  "name": "AnonymousPotion $VERSION",
  "version_number": "$VERSION",
  "changelog": $CHANGELOG,
  "dependencies": [],
  "game_versions": $GAME_VERSIONS,
  "version_type": "release",
  "loaders": ["paper", "purpur", "folia"],
  "featured": true,
  "project_id": "$PROJECT_ID",
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
