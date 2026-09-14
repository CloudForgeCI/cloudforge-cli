#!/usr/bin/env bash
# Builds target/classes + target/dependency (via `mvn package`) and packages them into the
# release/cloudforge-cli-<version>-darwin.tar.gz layout the Homebrew formula expects: a
# libexec/classes + libexec/dependency tree plus a libexec/bin/cloudforge-cli launcher script.
# Same classpath-launch shape cfc-testing/InteractiveDeployer and cloudforge-synth-service already
# use -- no shaded jar. Used both locally and by .github/workflows/release.yml, so the tarball this
# script produces is identical either way.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

VERSION="$(mvn -q -o help:evaluate -Dexpression=project.version -DforceStdout)"

mvn -B -o clean package -DskipTests

rm -rf release
STAGE="release/cloudforge-cli-${VERSION}/libexec"
mkdir -p "$STAGE/bin"
cp -r target/classes "$STAGE/classes"
cp -r target/dependency "$STAGE/dependency"

cat > "$STAGE/bin/cloudforge-cli" <<'SCRIPT'
#!/usr/bin/env bash
# Launches Main (deploy/emulator subcommand dispatch) on the classpath this release ships --
# java -cp classes:dependency/*, the same classpath-launch convention cfc-testing's
# InteractiveDeployer and cloudforge-synth-service already use, no shaded jar. Requires `java`
# (25+) and `node` on PATH; jsii/aws-cdk-lib synthesis (the `deploy` subcommand only) spawns node
# itself, this wrapper never touches it directly.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec java -cp "$DIR/classes:$DIR/dependency/*" com.cloudforgeci.cli.Main "$@"
SCRIPT
chmod +x "$STAGE/bin/cloudforge-cli"

cd release
TARBALL="cloudforge-cli-${VERSION}-darwin.tar.gz"
tar czf "$TARBALL" "cloudforge-cli-${VERSION}"
SHA256="$(shasum -a 256 "$TARBALL" | awk '{print $1}')"

echo "version=$VERSION"
echo "tarball=release/$TARBALL"
echo "sha256=$SHA256"
