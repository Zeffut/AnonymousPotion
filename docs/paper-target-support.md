# Paper target support

AnonymousPotion is built against each Paper API target separately. The API dependency and Java toolchain are selected with `-PpaperTarget`:

| Minecraft / Paper | Paper API coordinate | Java toolchain | Status |
|---|---|---:|---|
| 1.21.11 | `1.21.11-R0.1-SNAPSHOT` | 21 | Existing supported target |
| 26.1.2 (stable build 74) | `26.1.2.build.74-stable` | 25 | Build and tests passed locally |
| 26.2 (stable build 129) | `26.2.build.129-stable` | 25 | Build and tests passed locally |
| 26.3 (beta build 174) | `26.3.build.174-beta` | 25 | Preview target only; not stable Paper support |

Paper 26.3 is a stable Minecraft release, but Paper build 174 is BETA (verified through the official Fill API and Maven metadata on 2026-10-11). The 26.3 target follows that channel: any publication must be labelled `beta`, not `release`. Stable Paper support requires a separately validated stable API and runtime build. Telemetry defaults and opt-out remain unchanged.

Run a target locally with `./gradlew clean test build -PpaperTarget=26.2`. The toolchain defaults to 21 for 1.21.11 and 25 for 26.x. A toolchain can be overridden for diagnosis with `-PjavaVersion=25`; that does not validate execution compatibility with Java 21. CI runs 1.21.11 on Java 21 and each 26.x target on Java 25.

## Verification record

On 2026-10-05, `./gradlew --no-daemon clean test build -PpaperTarget=26.1.2` and the equivalent command for `26.2` both initially failed dependency resolution because the Paper coordinates omitted the `-stable` suffix. The exact Gradle error was `Could not find io.papermc.paper:paper-api:26.1.2.build.74` (and correspondingly `26.2.build.129`). The coordinates were corrected to the stable version identifiers from Paper's published Maven metadata; both full commands then completed with `BUILD SUCCESSFUL`.

The initial 1.21.11 build failed before compilation because this runner has a Java 21 JRE but no Java 21 JDK. Gradle's exact error was `Cannot find a Java installation on your machine (Linux 6.8.0-142-generic amd64) matching: {languageVersion=21, vendor=any vendor, implementation=vendor-specific, nativeImageCapable=false}. Toolchain download repositories have not been configured.` Toolchain discovery confirmed `/usr/lib/jvm/java-21-openjdk-amd64` has no `javac`; Java 25 JDK is installed at `/usr/lib/jvm/java-25-openjdk-amd64`. With `-PjavaVersion=25`, the 1.21.11 API build, test, and jar tasks all succeeded. The configured CI matrix still tests this target on Java 21; local Java 25 success does not replace that runtime/compiler check.

Raw failure excerpts preserved from the Gradle output:

```text
> Could not find io.papermc.paper:paper-api:26.1.2.build.74.
> Could not find io.papermc.paper:paper-api:26.2.build.129.
> Cannot find a Java installation on your machine (Linux 6.8.0-142-generic amd64) matching: {languageVersion=21, vendor=any vendor, implementation=vendor-specific, nativeImageCapable=false}. Toolchain download repositories have not been configured.
```

After adding `-stable` to the 26.x API coordinates, each 26.x command printed `BUILD SUCCESSFUL`; the 1.21.11 command with `-PjavaVersion=25` also printed `BUILD SUCCESSFUL`.
