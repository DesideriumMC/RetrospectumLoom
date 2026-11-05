# Retrospectum Loom

Gradle tooling for Minecraft Forge 1.6.4 / 9.11.1.1345, providing mappings,
decompilation, Mixin annotation processing, SRG remapping and IDE run configurations.

## Requirements

| Purpose | JDK |
| --- | --- |
| Gradle 8.14.6 | 17 |
| Compilation and annotation processing | 8 |
| Target API and default game runtime | 7 |

Mod classes use Java 6 bytecode for Forge's class scanner. The game runs on Java 7 or 8.

## Build

```sh
./gradlew test build
./gradlew publish
./gradlew -Pversion=0.1.0 loomDistribution
```

The plugin JAR is `build/libs/retrospectum-loom-0.1.0.jar`.
`publish` writes the plugin and marker artifacts to `build/maven/`.
The implementation coordinate is `dev.desiderium.retro:retrospectum-loom:0.1.0`.

The `loomDistribution` task packages the plugin, marker and dependency metadata
as a local Maven repository ZIP under `build/distributions/`. Extract it into
a mod project's `libs/` directory to use `libs/loom-maven/` as a plugin repository.

## Develop a mod

Start with [Retrospectum Mod Template](https://github.com/DesideriumMC/RetrospectumModTemplate).
It includes the Gradle Wrapper, example mod and Mixin configuration. Run its Windows or Linux setup script to discover or download the required JDKs.

```groovy
plugins { id 'dev.desiderium.retro.loom' }

retrospectum.mixin('main', 'mixins.example.refmap.json')
retrospectum.mixinConfig('mixins.example.json')
```

```sh
./gradlew build genSources genIdeaRuns
./gradlew runClient
./gradlew runServer
```

Distribute the SRG release JAR produced by `remapJarForRelease`.
Use `mixinBootstrap` for the shared runtime and `annotationProcessor` for the Mixin processor.

## License

MIT. See [upstream projects and notices](UPSTREAM.md).

## Java discovery

Loom resolves the Java 7 runtime API from `java7Home`, a project-local `.jdks/7`,
or Gradle Toolchains, in that order. The compiler/AP use a Java 8 toolchain and
the game defaults to a Java 7 launcher. The mod template's setup scripts install
missing JDKs and configure local toolchain discovery automatically.

## Configuration and caches

The supported plugin ID is `dev.desiderium.retro.loom`, with the `retrospectum`
DSL. Optional build flags use `retrospectum.*` (for example,
`-Pretrospectum.offline`). Global cache entries use `caches/retrospectum-loom`;
project entries use `.gradle/retrospectum-loom-cache`. Regenerate caches after
upgrading from earlier checkouts.

## CI artifacts

GitHub Actions runs on pushes, pull requests and manual dispatch, on Linux and
Windows. It runs tests, validates the plugin and packages version `0.1.0`.
Download the plugin, sources, Javadoc and portable Maven repository ZIP from the
workflow's **Artifacts** section. Test reports are uploaded even on failure.
CI does not publish to a remote Maven repository or create GitHub releases.

Override the version for a future build with `-Pversion=<version>`.

For a version override, set `RELEASE_VERSION` to the same value when running
`python scripts/verify_artifacts.py`. CI passes this value to Gradle as `-Pversion`.

## Hosted Maven releases

The public repository is `https://maven.desiderium.dev/releases/`.
The **Release Maven** workflow verifies Linux and Windows builds and checks R2
credentials. Enable its **publish** input to upload the Linux Maven artifacts.
Use a new stable `MAJOR.MINOR.PATCH` version for changed binaries. Existing files
are accepted only when identical, so interrupted uploads can be retried. Metadata
updates preserve earlier versions and run serially within this repository.

Configure organization or repository Actions secrets `R2_ACCESS_KEY_ID` and
`R2_SECRET_ACCESS_KEY`, restricted to the `desiderium-maven` bucket. Organization
secrets must grant this repository access. A run with **publish** disabled builds
and checks credentials without uploading artifacts.
