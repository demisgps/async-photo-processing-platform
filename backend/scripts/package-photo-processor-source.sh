#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BACKEND_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PROCESSOR_DIR="$BACKEND_DIR/photo-processor"
OUTPUT_DIR="$BACKEND_DIR/target/cloud-functions/photo-processor"
STAGING_DIR="$OUTPUT_DIR/bundle"
ZIP_PATH="$OUTPUT_DIR/photo-processor-source.zip"
CHECKSUM_PATH="$ZIP_PATH.sha256"

command -v git >/dev/null 2>&1 || {
  echo "Erro: o comando git é obrigatório para gerar o ZIP reproduzível." >&2
  exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
  echo "Erro: o comando sha256sum é obrigatório para gerar o checksum." >&2
  exit 1
}

rm -rf "$STAGING_DIR"
rm -f "$ZIP_PATH" "$CHECKSUM_PATH"
mkdir -p "$STAGING_DIR/parent" "$STAGING_DIR/src/main"

cp "$BACKEND_DIR/pom.xml" "$STAGING_DIR/parent/pom.xml"
cp "$PROCESSOR_DIR/pom.xml" "$STAGING_DIR/pom.xml"

if grep -q '<relativePath>' "$STAGING_DIR/pom.xml"; then
  echo "Erro: o POM do photo-processor já declara relativePath; revise o empacotador." >&2
  exit 1
fi

temporary_pom="$STAGING_DIR/pom.xml.tmp"
awk '
  /<parent>/ { in_parent = 1 }
  in_parent && /<\/parent>/ {
    print "    <relativePath>parent/pom.xml</relativePath>"
    in_parent = 0
  }
  { print }
' "$STAGING_DIR/pom.xml" > "$temporary_pom"
mv "$temporary_pom" "$STAGING_DIR/pom.xml"

relative_path_count=$(grep -c '<relativePath>parent/pom.xml</relativePath>' "$STAGING_DIR/pom.xml" || true)
if [ "$relative_path_count" -ne 1 ]; then
  echo "Erro: não foi possível configurar exatamente um relativePath no POM temporário." >&2
  exit 1
fi

cp "$BACKEND_DIR/mvnw" "$STAGING_DIR/mvnw"
cp -R "$BACKEND_DIR/.mvn" "$STAGING_DIR/.mvn"
cp -R "$PROCESSOR_DIR/src/main/java" "$STAGING_DIR/src/main/java"
cp -R "$PROCESSOR_DIR/src/main/resources" "$STAGING_DIR/src/main/resources"
chmod +x "$STAGING_DIR/mvnw"

archive_git_dir="$OUTPUT_DIR/.bundle-git"
archive_index="$OUTPUT_DIR/.bundle-index"
cleanup_archive_metadata() {
  rm -rf "$archive_git_dir"
  rm -f "$archive_index"
}
trap cleanup_archive_metadata EXIT HUP INT TERM
cleanup_archive_metadata

# Um índice Git temporário preserva o modo executável do mvnw. O commit sintético
# usa identidade e data fixas para produzir o mesmo ZIP para o mesmo conteúdo.
git init --bare --quiet "$archive_git_dir"
GIT_DIR="$archive_git_dir" GIT_WORK_TREE="$STAGING_DIR" GIT_INDEX_FILE="$archive_index" \
  git add -- pom.xml parent mvnw .mvn src
mvnw_mode=$(GIT_DIR="$archive_git_dir" GIT_INDEX_FILE="$archive_index" \
  git ls-files --stage mvnw | cut -d ' ' -f 1)
if [ "$mvnw_mode" != "100755" ]; then
  echo "Erro: o mvnw não foi registrado como executável no ZIP." >&2
  exit 1
fi
tree=$(GIT_DIR="$archive_git_dir" GIT_INDEX_FILE="$archive_index" git write-tree)
commit=$(printf 'photo-processor source bundle\n' | \
  GIT_DIR="$archive_git_dir" \
  GIT_AUTHOR_NAME=source-bundle GIT_AUTHOR_EMAIL=source-bundle@localhost \
  GIT_COMMITTER_NAME=source-bundle GIT_COMMITTER_EMAIL=source-bundle@localhost \
  GIT_AUTHOR_DATE=1980-01-01T00:00:02Z GIT_COMMITTER_DATE=1980-01-01T00:00:02Z \
  git commit-tree "$tree")
GIT_DIR="$archive_git_dir" git archive --format=zip --output="$ZIP_PATH" "$commit"
cleanup_archive_metadata
trap - EXIT HUP INT TERM

(
  cd "$OUTPUT_DIR"
  sha256sum "$(basename "$ZIP_PATH")" > "$(basename "$CHECKSUM_PATH")"
)

checksum=$(cut -d ' ' -f 1 "$CHECKSUM_PATH")
printf 'Bundle: %s\n' "$STAGING_DIR"
printf 'ZIP: %s\n' "$ZIP_PATH"
printf 'SHA-256: %s\n' "$checksum"
