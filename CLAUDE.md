# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working in this repository.

## Project Overview

Sprinter is an IntelliJ IDEA plugin that allows running tests in a **reused JVM process** to skip expensive initialization time (e.g., Spring context loading) on sequential test runs. It works via hotswap — the JVM stays alive between test runs, code is hotswapped, then the next set of tests is sent through a remote test executor.

Forked from the abandoned `wrike/sprinter-idea-plugin`, this version tracks current IntelliJ releases (since build 243, up to 252.*). Targets Kotlin 2.3.20 with K2 support enabled.

## Gradle Commands

```powershell
.\gradlew buildPlugin     # Build the .zip distribution
.\gradlew runIde         # Launch a sandboxed IDEA with the plugin loaded
.\gradlew check          # Run tests + verification (equivalent to test + verify)
.\gradlew verifyPlugin   # Run IntelliJ Plugin Verifier against recommended IDE versions
```

There are **no unit tests** in this project — the `src/test` directory is empty. Testing is done by running the plugin in a sandboxed IDEA instance (`runIde`). The CI runs UI tests via a separate workflow (`run-ui-tests.yml`) using the IntelliJ Robot Framework on port 8082.

## Architecture

### Core Flow
1. User selects "Run in Launched JVM" (line marker contributed by `SameJvmRunLineMarkerContributor` / `KotlinSameJvmRunLineMarkerContributor`)
2. `SharedJvmExecutorService` checks if a shared JVM is already running
3. If yes: builds only the affected modules, triggers hotswap, then sends an execute-test command via the in-JVM test executor
4. If no: launches a new debug session with a remote test starter inside it

### Extension Point
The plugin defines `testFrameworkForRunningInSharedJVM` (`TestFrameworkForRunningInSharedJVM` interface) — each supported test framework implements this to provide its own `AbstractSharedJvmProcess` and `AbstractSharedJvmRunnableState`. Current implementations: **JUnit** and **TestNG**, registered in separate optional config files (`sprinter-JUnit.xml`, `sprinter-TestNGJ.xml`).

### Key Classes
- `SharedJvmExecutorService` — orchestrates the entire flow: starts JVM, triggers hotswap, sends test commands
- `SharedJvmConfiguration` / `SharedJvmConfigurationType` — run configuration type and its settings
- `SharedJvmConfigurationProducer` — dynamically creates SharedJvm configurations from existing test configs
- `DCEVMParametersPatcher` — `JavaProgramPatcher` that injects DCEVM/HotswapAgent JVM args into selected run configurations
- `AbstractSharedJvmProcess` — manages the long-lived debug process and test console UI per framework

### Settings Layer (`settings/` package)
- `ModulesWithHotSwapAgentPluginsService` / `ConfigurationsToAttachHAService` — persist which modules/configurations should get HotswapAgent injected
- `SharedJvmSettingsConfigurable` — Tools > Sprinter Settings dialog
- `ConfigurationChangeListener` / `ModuleChangeListener` — project listeners for run config and module changes

### Resources
- `plugin.xml` — main descriptor with extension points, actions, services, and listeners
- `sprinter-JUnit.xml`, `sprinter-TestNGJ.xml`, `sprinter-Kotlin.xml` — optional dependency config files (loaded only when those plugins are installed)
- `messages.properties` / `BUNDLE.kt` — localized messages via IntelliJ resource bundle pattern
