---
name: build-app
description: Build the FishClassification Android app with Gradle. Use this skill whenever you need to compile the project, verify a code change compiles, produce a debug or release APK, install on a connected device, run lint, or clean the build. Trigger on phrases like "build the app", "compile", "make sure it builds", "assembleDebug", "build APK", "install on device", "run lint", "gradle clean", or whenever you finish editing Kotlin/Gradle/manifest/resource files and need to confirm the project still builds.
---

# Build the FishClassification Android app

This project uses the Gradle wrapper. The build is straightforward **once two project-specific quirks are handled**: the Java toolchain is not on PATH by default, and one TensorFlow Lite library is intentionally excluded.

## Required environment

`./gradlew` will fail with `ERROR: JAVA_HOME is not set` if you call it directly. Always export the Android Studio bundled JDK first. Do this in the same shell command (Bash tool resets shell state between calls):

```bash
export JAVA_HOME=/opt/android-studio/jbr && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew <task>
```

The JBR (JetBrains Runtime) shipped with Android Studio at `/opt/android-studio/jbr` is the supported JDK for AGP 9. Don't substitute another Java unless you know the AGP/JDK compatibility matrix — AGP 9.1.1 is picky.

## Common tasks

| Task | Command (after env export) |
|------|----------------------------|
| Compile + assemble debug APK | `./gradlew assembleDebug` |
| Quick compile check (no APK packaging) | `./gradlew compileDebugKotlin` |
| Release APK | `./gradlew assembleRelease` |
| Install on connected device/emulator | `./gradlew installDebug` |
| Lint | `./gradlew lintDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Clean | `./gradlew clean` |

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Recommended invocation pattern

Output is verbose. Tail it to keep context lean:

```bash
export JAVA_HOME=/opt/android-studio/jbr && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew assembleDebug 2>&1 | tail -60
```

Look for `BUILD SUCCESSFUL` or `BUILD FAILED` in the tail. On failure, increase the tail to 200 to see the actual error stack.

### First build is slow

A cold build (no Gradle daemon, no caches) takes 2–8 minutes. Subsequent builds with the daemon warm are 10–30 seconds. For the first build of a session, run it via Bash with `run_in_background: true` and wait for the task notification — don't block on a 2-minute foreground call.

### Notifications for long builds

The user has a global rule: any process >2 minutes should send a desktop notification on completion. After a long build:

```bash
notify-send "Claude Code" "Android build finished"
```

## Pitfalls to avoid

### Don't add `tensorflow-lite-support` back

`org.tensorflow:tensorflow-lite-support` declares a namespace that collides with `tensorflow-lite-support-api` under AGP 9, breaking the manifest merger. The dependency is intentionally commented out in `app/build.gradle.kts`. The library alias still exists in `gradle/libs.versions.toml` but the `implementation(libs.tensorflow.lite.support)` line is disabled. Don't re-enable it without first solving the namespace collision (e.g., via dependency exclusion or upgrading to a version where this is fixed).

If you need TFLite helpers (e.g., `FileUtil.loadMappedFile`, `ImageProcessor`), implement them inline — see `ml/TFLiteHelper.kt` for an example of `loadModelFile` using `AssetFileDescriptor` directly.

### `.tflite` files are not compressed

`androidResources { noCompress += "tflite" }` is set in `app/build.gradle.kts` so the model can be memory-mapped from the APK. Don't remove this — the interpreter requires the file to be aligned and uncompressed.

### Don't use `--no-daemon` casually

The Gradle daemon makes incremental builds 10× faster. Only disable it if you suspect daemon corruption (run `./gradlew --stop` to restart instead).

## Reading build failures

Common failure shapes:

- **Kotlin compile error**: search the tail for `error: ` — the file:line is shown.
- **Manifest merger failure**: shows `Error: ... at AndroidManifest.xml`. Often caused by namespace collisions between dependencies (see tflite-support warning above).
- **Unresolved reference**: missing dependency in `app/build.gradle.kts` or wrong import. Check `gradle/libs.versions.toml` to confirm the alias exists.
- **Resource linking failure**: typically a missing `@drawable/`, `@string/`, or theme reference. Look for `error: resource ... not found`.

For lint failures, the report is at `app/build/reports/lint-results-debug.html` (open with the user's preferred viewer or `cat` the corresponding `.txt` variant).

## Quick sanity check

If you want a fast "does the project still compile" check after editing files, this is the lightest reliable command:

```bash
export JAVA_HOME=/opt/android-studio/jbr && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew compileDebugKotlin 2>&1 | tail -40
```

It skips APK packaging, dexing, and resource processing — perfect for iterating on Kotlin changes.
