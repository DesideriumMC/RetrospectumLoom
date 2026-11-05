# Loom reference

Start from RetrospectumModTemplate: the README DSL fragment assumes its
`settings.gradle` pluginManagement repositories and plugin version selection.
The supported plugin ID is `dev.desiderium.retro.loom`; configuration uses
`retrospectum`. Build-runtime dependencies remain explicit in the consuming build.

## Mixin

- `retrospectum.mixin('main', 'example.refmap.json')` enables AP mappings and packages the refmap.
- `retrospectum.mixinConfig('example.mixins.json')` adds manifest discovery and development registration.
- `mixinBootstrap` supplies the shared runtime; `annotationProcessor` supplies the official Mixin processor.

## Dependency configurations

| Configuration | Purpose |
| --- | --- |
| `minecraft`, `forge`, `mappings` | Original game, Forge universal and source mapping archives |
| `modImplementation` | Remap a mod dependency for compile and runtime |
| `modCompileOnly` | Remap a compile-only mod dependency |
| `modRuntimeOnly` | Remap a runtime-only mod dependency |
| `coremodImplementation`, `coremodRuntimeOnly` | Coremod dependencies copied into the development installation |
| `implementation`, `compileOnly`, `runtimeOnly` | Standard library dependencies |

## Tasks

| Task | Result |
| --- | --- |
| `build` / `remapJarForRelease` | Validated SRG release archive |
| `exportReleaseMappings` | Stable MCP to SRG mapping input for release remapping |
| `jar` | Development archive |
| `genSources` | Decompiled sources attached through the local IDE module |
| `runClient`, `runServer` | Gradle-managed development launch |
| `genIdeaRuns` | Preserved-on-regeneration IDEA Gradle launchers |
| `genEclipseRuns`, `vscode` | Direct Java IDE launches; build mappings first |
| `exportDevelopmentLaunches` | `build/retromixin/launches.json` for runtime verification |

Publishing is opt-in: apply `maven-publish`, then use
`publishing { publications { mod(MavenPublication) { from components.java } } }`.
Both Java variants expose the release archive. A guard rejects publication of
the development archive. Game dependencies stay on development classpaths.

`--offline` or `-Pretrospectum.offline` selects cached inputs; it does not make
missing artifacts available. `--refresh-dependencies` or
`-Pretrospectum.refreshDependencies` requests refreshed inputs.
`java7Home` overrides the target API JDK; Gradle toolchain properties select the
compiler and launcher installations. See the compatibility table for supported versions.

Remapped mod dependencies publish their original distribution coordinates at runtime scope.
`modCompileOnly` stays compile-only and is not published transitively.

Cache locks are centralized under
`GRADLE_USER_HOME/caches/retrospectum-loom/locks/<target-path-sha256>.lock`.
Downloads and providers use the same canonical target identity, including nested
provider downloads. The old adjacent lock protocol is no longer used. Lock files
remain after normal unlock to keep cross-process coordination stable.
