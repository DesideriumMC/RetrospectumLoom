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
./gradlew -Pversion=0.1.0-alpha.1 loomDistribution
```

The plugin JAR is `build/libs/retrospectum-loom-0.1.0-SNAPSHOT.jar`.
`publish` writes the plugin and marker artifacts to `build/maven/`.
The implementation coordinate is `dev.desiderium.retro:retrospectum-loom:0.1.0-SNAPSHOT`.

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
