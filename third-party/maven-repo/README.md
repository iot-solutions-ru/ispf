# Third-party Maven mirror

Local Maven layout used by the root `build.gradle.kts` repository `third-party-local`.

## Why

Some legacy transitive artifacts are no longer available from their original public repositories.
Artifacts here keep affected builds resolvable offline.

## Contents

| Coordinates | License / notes |
|-------------|-----------------|
| `javax.media.jai:com.springsource.javax.media.jai.core:1.1.3` | YARG transitive (was on cuba-platform) |
| `javax.media.jai:com.springsource.javax.media.jai.codec:1.1.3` | YARG transitive |

See [third-party-notices](../../docs/en/third-party-notices.md).
