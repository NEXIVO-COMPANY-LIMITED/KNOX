# Task 5 fix round 1/5

## Open findings

### Important — ALLOW_DO_RELEASE on labRelease
`productFlavors.lab` sets `ALLOW_DO_RELEASE=true` for all lab build types, including `labRelease`. Spec/Task 1 require production/release fail-closed for DO release. Lab escape hatch must be **labDebug only**.

**Fix:** In `app/build.gradle.kts`, set flavor default `ALLOW_DO_RELEASE=false` for both flavors. In `buildTypes.debug`, after flavors, set `ALLOW_DO_RELEASE=true` only for lab debug — use:

```kotlin
// After productFlavors / in android.applicationVariants or:
buildTypes {
  debug {
    // ...
  }
  release {
    // ensure ALLOW_DO_RELEASE false (already)
  }
}
// Prefer matchingDimensions:
productFlavors {
  create("lab") {
    // remove ALLOW_DO_RELEASE true here — keep false
  }
}
```

Simplest correct approach:
1. Remove `buildConfigField ALLOW_DO_RELEASE true` from `lab` flavor (leave false).
2. Add in `debug` buildType:
```kotlin
matchingFallbacks ... 
```
Actually Gradle merges BuildConfig — last wins by variant. Use:

```kotlin
androidComponents {
  onVariants(selector().withFlavor("channel" to "lab").withBuildType("debug")) { variant ->
    variant.buildConfigFields?.put("ALLOW_DO_RELEASE", ...)
  }
}
```

Or simpler without androidComponents: in `buildTypes.debug` keep false, and add a `lab` source set BuildConfig is hard.

**Recommended simple fix used in many projects:**
```kotlin
productFlavors {
  create("lab") {
    buildConfigField("boolean", "ALLOW_DO_RELEASE", "false") // default
  }
}
buildTypes {
  debug {
    // cannot easily set per-flavor here in older AGP without variants API
  }
}
```

Use AGP `applicationVariants.all` (works with AGP 8):
```kotlin
android.applicationVariants.configureEach {
  val isLabDebug = flavorName.contains("lab") && buildType.name == "debug"
  buildConfigField("boolean", "ALLOW_DO_RELEASE", if (isLabDebug) "true" else "false")
}
```
Place after `android { }` block using:
```kotlin
androidComponents {
  onVariants(selector().all()) { variant ->
    val labDebug = variant.flavorName == "lab" && variant.buildType == "debug"
    // For AGP 8.5 kotlin DSL - check project pattern
  }
}
```

**Pragmatic fix if variants API is painful:** Keep `lab` flavor `ALLOW_DO_RELEASE=true` BUT force `release` buildType to always set `ALLOW_DO_RELEASE` to `"false"` (buildType fields override flavor in AGP for same key when both set — verify). In AGP, build type and flavor BuildConfig fields merge; **duplicate keys: typically build type does not override flavor**. So explicitly in release:

Actually the cleanest: only set true in `src/labDebug/java` is wrong for BuildConfig.

Do this in `app/build.gradle.kts` after the android block:

```kotlin
android.applicationVariants.configureEach {
  val allow = (productFlavors.any { it.name == "lab" } && buildType.name == "debug")
  // Deprecated but works - OR use:
}
```

With Kotlin DSL + AGP 8.5:
```kotlin
androidComponents {
    onVariants(selector().withBuildType("debug").withFlavor("channel", "lab")) {
        it.buildConfigFields!!.put(
            "ALLOW_DO_RELEASE",
            com.android.build.api.variant.BuildConfigField("boolean", "true", "")
        )
    }
}
```

Implement whatever compiles. Add a unit test or document in LabDeviceOwnerRelease KDoc: enabled only for labDebug.

Also update Task 1 test KDoc if it claims debug ALLOW_DO_RELEASE=true for all debug — now only labDebug.

Re-run SecurityCoordinatorTest and DeviceAuthGateTest.
Do NOT commit.

Append fix notes to task-5-report.md.
