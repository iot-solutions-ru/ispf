# Third-party Maven mirror

Local Maven layout used by the root `build.gradle.kts` repository `third-party-local`.

## Why

`https://maven.mangoautomation.net` (BACnet4J / `lohbihler`) is frequently unavailable (503/401) in CI.
Artifacts here keep `ispf-driver-bacnet` resolvable offline.

## Contents

| Coordinates | License / notes |
|-------------|-----------------|
| `com.infiniteautomation:bacnet4j:6.1.0` | GPL-3.0 |
| `lohbihler:sero-scheduler:1.1.0` | BACnet4J transitive |
| `lohbihler:sero-warp:1.0.0` | BACnet4J transitive |
| `javax.media.jai:com.springsource.javax.media.jai.core:1.1.3` | YARG transitive (was on cuba-platform) |
| `javax.media.jai:com.springsource.javax.media.jai.codec:1.1.3` | YARG transitive |

See [third-party-notices](../../docs/en/third-party-notices.md). Remote `ias-releases` remains a fallback when the host is up.
