# Custom driver template

This directory is a Gradle build. It compiles against `com.ispf:ispf-driver-api` and `com.ispf:ispf-core` from the local Maven repository.

From an ISPF checkout of the same version:

```bash
./gradlew publishDdkApi
```

`publishDdkApi` writes both modules to `~/.m2` at the `version` in the ISPF `gradle.properties`. Set `ispfVersion` in this directory's `gradle.properties` to that version.

JDK 25 and Gradle 9.9:

```bash
gradle test
gradle assembleDriverPack
```

The pack directory is `build/driver-pack/ispf-driver-acme-widget/` (`driver-pack.json` and `ispf-driver-acme-widget.jar`). Copy it into `ISPF_DRIVER_PACKS_DIR` and restart the server.

Replace `acme-widget` and `com.ispf.driver.template` with your driver. Change `packId` in this `build.gradle.kts`, `rootProject.name` in `settings.gradle.kts`, and in `driver-pack.json` the fields `packId`, `drivers[].driverId`, and `drivers[].driverClass`. `assembleDriverPack` writes `$packId.jar`. Set `jarFile` to that name. `driverClass` is the class the server loads.

A driver that stays in the ISPF tree is a new `packages/ispf-driver-<id>` module. Its `build.gradle.kts` depends on `project(":packages:ispf-driver-api")`, and `gradle/driver-packs.json` needs a row for that module name. The root build then applies `ispf-driver-pack`. Do not add this template directory to the root `settings.gradle.kts`.
