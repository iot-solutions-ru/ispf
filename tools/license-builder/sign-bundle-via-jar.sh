#!/usr/bin/env bash
# Sign a bundle using the same BundleManifest DTO projection as ispf-server 0.9.186 deploy.
# Prefer tools/license-builder/sign-bundle.py once the server hashes the raw JSON request map
# (ApplicationController deploy fix). Until that jar is on the host, use this helper.
#
# Usage:
#   ISPF_SERVER_JAR=/opt/ispf/ispf-server.jar \
#   bash tools/license-builder/sign-bundle-via-jar.sh \
#     --bundle examples/mes-platform-production/bundle.json \
#     --bundle-id mes-platform-production \
#     --installation-id <hex> \
#     --private-key /opt/ispf/keys/license-private.pem \
#     --out /tmp/signed.json
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
JAR="${ISPF_SERVER_JAR:-/opt/ispf/ispf-server.jar}"
WORKDIR="${SIGN_BUNDLE_WORKDIR:-/tmp/ispf-sign-bundle-$$}"
BUNDLE=""
BUNDLE_ID=""
INSTALL_ID=""
PRIVATE_KEY=""
OUT=""
MIN_VER="0.9.0"

while [ $# -gt 0 ]; do
  case "$1" in
    --bundle) BUNDLE="$2"; shift 2 ;;
    --bundle-id) BUNDLE_ID="$2"; shift 2 ;;
    --installation-id) INSTALL_ID="$2"; shift 2 ;;
    --private-key) PRIVATE_KEY="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --min-platform-version) MIN_VER="$2"; shift 2 ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

for v in BUNDLE BUNDLE_ID INSTALL_ID PRIVATE_KEY OUT; do
  if [ -z "${!v}" ]; then
    echo "Missing --$(echo "$v" | tr 'A-Z_' 'a-z-')" >&2
    exit 1
  fi
done
if [ ! -f "$JAR" ]; then
  echo "Missing jar: $JAR" >&2
  exit 1
fi

mkdir -p "$WORKDIR"
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

cd "$WORKDIR"
jar xf "$JAR"
cat > SignMain.java <<'JAVA'
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleManifest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class SignMain {
  static final ObjectMapper OM = new ObjectMapper();

  @SuppressWarnings("unchecked")
  static Map<String, Object> stripNulls(Map<String, Object> source) {
    Map<String, Object> cleaned = new LinkedHashMap<>();
    for (var e : source.entrySet()) {
      Object v = e.getValue();
      if (v == null) {
        continue;
      }
      if (v instanceof Map<?, ?> nested) {
        cleaned.put(e.getKey(), stripNulls((Map<String, Object>) nested));
      } else {
        cleaned.put(e.getKey(), v);
      }
    }
    return cleaned;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> sortRecursively(Map<String, Object> source) {
    Map<String, Object> sorted = new TreeMap<>();
    for (var e : source.entrySet()) {
      Object v = e.getValue();
      if (v instanceof Map<?, ?> nested) {
        sorted.put(e.getKey(), sortRecursively((Map<String, Object>) nested));
      } else {
        sorted.put(e.getKey(), v);
      }
    }
    return sorted;
  }

  static String sha256Hex(String s) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  static PrivateKey loadPrivateKey(String pem) throws Exception {
    String normalized = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    byte[] decoded = Base64.getDecoder().decode(normalized);
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
  }

  public static void main(String[] args) throws Exception {
    String bundlePath = args[0];
    String appId = args[1];
    String installationId = args[2];
    String privateKeyPath = args[3];
    String outPath = args[4];
    String minVer = args[5];

    String json = Files.readString(Path.of(bundlePath));
    BundleManifest typed = OM.readValue(json, BundleManifest.class);
    Map<String, Object> root = OM.convertValue(typed, new TypeReference<>() {});
    root.remove("license");
    String canonical = OM.writeValueAsString(sortRecursively(stripNulls(root)));
    String contentSha256 = sha256Hex(canonical);

    String expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString();
    Map<String, String> claims = new TreeMap<>();
    claims.put("bundleId", appId);
    claims.put("minPlatformVersion", minVer);
    claims.put("installationId", installationId);
    claims.put("contentSha256", contentSha256);
    claims.put("expiresAt", expiresAt);
    String payload = OM.writeValueAsString(claims);

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(loadPrivateKey(Files.readString(Path.of(privateKeyPath))));
    signature.update(payload.getBytes(StandardCharsets.UTF_8));
    Map<String, Object> license = new LinkedHashMap<>(claims);
    license.put("signature", Base64.getEncoder().encodeToString(signature.sign()));

    Map<String, Object> raw = OM.readValue(json, new TypeReference<>() {});
    raw.put("license", license);
    Files.writeString(Path.of(outPath), OM.writerWithDefaultPrettyPrinter().writeValueAsString(raw) + "\n");
    System.out.println("Wrote " + outPath);
    System.out.println("contentSha256=" + contentSha256);
  }
}
JAVA

CP="BOOT-INF/classes:BOOT-INF/lib/*"
javac --release 25 -cp "$CP" SignMain.java
java -cp ".:$CP" SignMain "$BUNDLE" "$BUNDLE_ID" "$INSTALL_ID" "$PRIVATE_KEY" "$OUT" "$MIN_VER"
