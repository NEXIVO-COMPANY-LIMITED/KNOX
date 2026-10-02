import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun configValue(propertyKey: String, envKey: String = propertyKey): String? {
    val fromFile = localProperties.getProperty(propertyKey)?.trim()?.takeIf { it.isNotEmpty() }
    val fromEnv = System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }
    // CI / packaging gates: prefer env when PAYO_PREFER_ENV_CONFIG=true (or CI=true).
    val preferEnv = System.getenv("PAYO_PREFER_ENV_CONFIG")?.equals("true", true) == true ||
        System.getenv("CI")?.equals("true", true) == true
    return if (preferEnv) (fromEnv ?: fromFile) else (fromFile ?: fromEnv)
}

fun String.toBuildConfigString(): String {
    val escaped = replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

fun looksLikePlaceholderPin(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    val v = value.trim()
    if (!v.startsWith("sha256/")) return true
    val b64 = v.removePrefix("sha256/")
    if (b64.length < 40) return true
    val upper = v.uppercase()
    if (upper.contains("YOUR-") || upper.contains("PLACEHOLDER") || upper.contains("CHANGEME") ||
        upper.contains("DUMMY") || upper.contains("EXAMPLE")
    ) {
        return true
    }
    if (Regex("A{16,}").containsMatchIn(upper)) return true
    if (Regex("0{16,}").containsMatchIn(b64)) return true
    // Primary check: OkHttp CertificatePinner SPKI pins are sha256(SPKI) → exactly 32 bytes.
    // Secondary heuristic only: reject "text disguised as a hash" (mostly printable ASCII).
    // Prefer tools/verify-cert-pins.py against a live host/PEM for real pin verification.
    return try {
        val decoded = Base64.getDecoder().decode(b64)
        if (decoded.size != 32) return true
        val printable = decoded.count { it in 32..126 }
        printable > 20
    } catch (_: IllegalArgumentException) {
        true
    }
}

/**
 * Shared prodRelease packaging checks (used by verify + negative tests).
 * Returns human-readable failure messages (empty = pass).
 */
fun prodReleasePackagingFailures(
    manifestText: String,
    buildConfigText: String,
    networkSecurityText: String?,
    scanFileTexts: Map<String, String>,
    apkExists: Boolean
): List<String> {
    val errors = mutableListOf<String>()
    if (manifestText.contains("android:debuggable=\"true\"")) {
        errors += "prodRelease must not be debuggable=true"
    }
    val labMarkers = listOf(
        "com.nexivo.payo.lab.CLEAR_DEVICE_OWNER",
        "com.nexivo.payo.lab.SET_AUTO_RELEASE",
        "com.nexivo.payo.lab.DUMP_POLICY_STATE",
        "LabAdbEscapeReceiver",
        "LabHardeningToggleReceiver",
        "LabAutoReleaseAlarmReceiver"
    )
    labMarkers.forEach { marker ->
        if (manifestText.contains(marker)) {
            errors += "prodRelease must not contain lab marker $marker"
        }
    }
    if (!buildConfigText.contains("boolean ALLOW_DO_RELEASE = false")) {
        errors += "prodRelease ALLOW_DO_RELEASE must be false"
    }
    val apiKey = Regex("""String SERVER_API_KEY = "([^"]*)";""").find(buildConfigText)?.groupValues?.get(1)
    if (apiKey == null) {
        errors += "SERVER_API_KEY field missing from prodRelease BuildConfig"
    } else if (apiKey.isNotEmpty()) {
        errors += "prodRelease SERVER_API_KEY must be empty"
    }
    if (!buildConfigText.contains("boolean CERT_PINNING_ENABLED = true")) {
        errors += "prodRelease CERT_PINNING_ENABLED must be true"
    }
    val primary = Regex("""String CERT_PIN_SHA256_PRIMARY = "([^"]+)";""")
        .find(buildConfigText)?.groupValues?.get(1)?.replace("\\\"", "\"")
    if (primary == null) {
        errors += "CERT_PIN_SHA256_PRIMARY missing"
    } else if (looksLikePlaceholderPin(primary)) {
        errors += "prodRelease primary pin looks like placeholder/dummy"
    }
    val backup = Regex("""String CERT_PIN_SHA256_BACKUP = "([^"]+)";""")
        .find(buildConfigText)?.groupValues?.get(1)?.replace("\\\"", "\"")
    if (backup == null) {
        errors += "CERT_PIN_SHA256_BACKUP missing"
    } else if (looksLikePlaceholderPin(backup)) {
        errors += "prodRelease backup pin looks like placeholder/dummy"
    }
    val url = Regex("""String SERVER_BASE_URL = "([^"]+)";""")
        .find(buildConfigText)?.groupValues?.get(1)?.replace("\\\"", "\"")
    if (url == null) {
        errors += "SERVER_BASE_URL missing"
    } else if (!url.lowercase().startsWith("https://")) {
        errors += "prodRelease SERVER_BASE_URL must be https"
    }
    val ns = networkSecurityText
    if (ns != null) {
        if (ns.contains("cleartextTrafficPermitted=\"true\"")) {
            errors += "prodRelease must not permit cleartext"
        }
    }
    if (!apkExists) {
        errors += "prodRelease APK missing"
    }
    val privateKeyMarkers = listOf(
        "BEGIN PRIVATE KEY",
        "BEGIN EC PRIVATE KEY",
        "BEGIN RSA PRIVATE KEY"
    )
    scanFileTexts.forEach { (path, text) ->
        privateKeyMarkers.forEach { marker ->
            if (text.contains(marker)) {
                errors += "Private key marker '$marker' in $path"
            }
        }
    }
    return errors
}

fun looksLikePlaceholderHost(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    val v = value.trim().lowercase()
    if (v.contains("your-real") || v.contains("placeholder") || v.contains("changeme")) return true
    return false
}

val googleServicesFile = file("google-services.json")
val hasGoogleServices = googleServicesFile.exists()

android {
    namespace = "com.nexivo.payo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexivo.payo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SERVER_BASE_URL", "\"\"")
        buildConfigField("String", "SERVER_API_KEY", "\"\"")
        buildConfigField("String", "CERT_PIN_HOST", "\"\"")
        buildConfigField("String", "CERT_PIN_SHA256_PRIMARY", "\"\"")
        buildConfigField("String", "CERT_PIN_SHA256_BACKUP", "\"\"")
        buildConfigField("boolean", "CERT_PINNING_ENABLED", "false")
        // Overridden per variant (androidComponents below): true only for labDebug.
        buildConfigField("boolean", "ALLOW_DO_RELEASE", "false")
        buildConfigField("boolean", "FIREBASE_AVAILABLE", hasGoogleServices.toString())
        // Optional APK-pinned command verification keys (public SPKI only — never private keys).
        buildConfigField("String", "COMMAND_PUBKEY_CURRENT_KID", "\"\"")
        buildConfigField("String", "COMMAND_PUBKEY_CURRENT_SPKI", "\"\"")
        buildConfigField("String", "COMMAND_PUBKEY_NEXT_KID", "\"\"")
        buildConfigField("String", "COMMAND_PUBKEY_NEXT_SPKI", "\"\"")
        /**
         * When true on API < 30, apply UserManager.DISALLOW_APPS_CONTROL (device-wide —
         * blocks Settings clear-data for *all* apps). Default false — prefer API 30+ UCDP.
         */
        buildConfigField("boolean", "APPLY_DISALLOW_APPS_CONTROL", "false")
        /**
         * When true, refuse Device Owner enrollment / post-provisioning on API < 30.
         * Default false (minSdk 26 lab path remains available).
         */
        buildConfigField("boolean", "REFUSE_DO_BELOW_API_30", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val storePath = configValue("STORE_FILE", "STORE_FILE")
            val storePasswordValue = configValue("STORE_PASSWORD", "STORE_PASSWORD")
            val keyAliasValue = configValue("KEY_ALIAS", "KEY_ALIAS")
            val keyPasswordValue = configValue("KEY_PASSWORD", "KEY_PASSWORD")
            if (!storePath.isNullOrBlank()) {
                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue ?: ""
                keyAlias = keyAliasValue ?: ""
                keyPassword = keyPasswordValue ?: storePasswordValue ?: ""
            }
        }
        // Lab channel: separate keystore when LAB_STORE_FILE is set; else Android debug keystore.
        create("lab") {
            val storePath = configValue("LAB_STORE_FILE", "LAB_STORE_FILE")
            val storePasswordValue = configValue("LAB_STORE_PASSWORD", "LAB_STORE_PASSWORD")
            val keyAliasValue = configValue("LAB_KEY_ALIAS", "LAB_KEY_ALIAS")
            val keyPasswordValue = configValue("LAB_KEY_PASSWORD", "LAB_KEY_PASSWORD")
            if (!storePath.isNullOrBlank()) {
                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue ?: ""
                keyAlias = keyAliasValue ?: ""
                keyPassword = keyPasswordValue ?: storePasswordValue ?: ""
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("prod") {
            dimension = "channel"
            buildConfigField("boolean", "ALLOW_DO_RELEASE", "false")
        }
        create("lab") {
            dimension = "channel"
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-lab"
            // ALLOW_DO_RELEASE stays false here; androidComponents enables it for labDebug only.
            buildConfigField("boolean", "ALLOW_DO_RELEASE", "false")
            val labSigning = signingConfigs.getByName("lab")
            if (labSigning.storeFile != null && labSigning.storeFile!!.exists()) {
                signingConfig = labSigning
            }
        }
    }

    buildTypes {
        debug {
            val serverBaseUrl = configValue("SERVER_BASE_URL") ?: ""
            val serverApiKey = configValue("SERVER_API_KEY") ?: ""
            buildConfigField("String", "SERVER_BASE_URL", serverBaseUrl.toBuildConfigString())
            buildConfigField("String", "SERVER_API_KEY", serverApiKey.toBuildConfigString())
            // Debug: certificate pinning disabled (no host/pin defaults).
            buildConfigField("String", "CERT_PIN_HOST", "\"\"")
            buildConfigField("String", "CERT_PIN_SHA256_PRIMARY", "\"\"")
            buildConfigField("String", "CERT_PIN_SHA256_BACKUP", "\"\"")
            buildConfigField("boolean", "CERT_PINNING_ENABLED", "false")
        }
        release {
            val serverBaseUrl = configValue("SERVER_BASE_URL") ?: ""
            val pinHost = configValue("CERT_PIN_HOST") ?: ""
            val pinPrimary = configValue("CERT_PIN_SHA256") ?: configValue("CERT_PIN_SHA256_PRIMARY") ?: ""
            val pinBackup = configValue("CERT_PIN_SHA256_BACKUP") ?: ""

            buildConfigField("String", "SERVER_BASE_URL", serverBaseUrl.toBuildConfigString())
            // Release never embeds SERVER_API_KEY — not read from local.properties or env.
            buildConfigField("String", "SERVER_API_KEY", "\"\"")
            buildConfigField("String", "CERT_PIN_HOST", pinHost.toBuildConfigString())
            buildConfigField("String", "CERT_PIN_SHA256_PRIMARY", pinPrimary.toBuildConfigString())
            buildConfigField("String", "CERT_PIN_SHA256_BACKUP", pinBackup.toBuildConfigString())
            buildConfigField("boolean", "CERT_PINNING_ENABLED", "true")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
            // else: unsigned/debug-signed release allowed for local minify verification
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // android.util.Log / other Android stubs return defaults in JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }
}

// Lab Device Owner release (unbrick test phones) is enabled for labDebug ONLY.
// prodDebug, prodRelease and labRelease always fail closed.
androidComponents {
    onVariants { variant ->
        val isLabDebug = variant.buildType == "debug" &&
            variant.productFlavors.any { (dimension, name) -> dimension == "channel" && name == "lab" }
        variant.buildConfigFields.put(
            "ALLOW_DO_RELEASE",
            com.android.build.api.variant.BuildConfigField("boolean", isLabDebug.toString(), null)
        )
    }
}

/**
 * Proves labDebug-only ADB escape is packaged for labDebug and absent from prodDebug + labRelease.
 */
tasks.register("verifyLabAdbEscapePackaging") {
    group = "verification"
    description = "Assert LabAdbEscapeReceiver is labDebug-only (absent prod + labRelease)."
    dependsOn(
        "processLabDebugMainManifest",
        "processProdDebugMainManifest",
        "processLabReleaseMainManifest",
        "generateLabDebugBuildConfig",
        "generateLabReleaseBuildConfig",
        "generateProdDebugBuildConfig",
        "assembleLabDebug"
    )
    doLast {
        val labDebugManifest = file(
            "build/intermediates/merged_manifest/labDebug/processLabDebugMainManifest/AndroidManifest.xml"
        )
        val prodDebugManifest = file(
            "build/intermediates/merged_manifest/prodDebug/processProdDebugMainManifest/AndroidManifest.xml"
        )
        val labReleaseManifest = file(
            "build/intermediates/merged_manifest/labRelease/processLabReleaseMainManifest/AndroidManifest.xml"
        )
        val labMarkers = listOf(
            "com.nexivo.payo.lab.CLEAR_DEVICE_OWNER",
            "com.nexivo.payo.lab.SET_AUTO_RELEASE",
            "com.nexivo.payo.lab.DUMP_POLICY_STATE",
            "LabAdbEscapeReceiver",
            "LabHardeningToggleReceiver",
            "LabAutoReleaseAlarmReceiver"
        )
        val labDebugText = labDebugManifest.readText()
        labMarkers.forEach { marker ->
            check(labDebugText.contains(marker)) {
                "labDebug manifest missing $marker"
            }
        }
        // Exported lab receivers must require DUMP (adb/shell).
        val dumpPermission = """android:permission="android.permission.DUMP""""
        check(labDebugText.contains(dumpPermission)) {
            "labDebug exported lab receivers must declare android:permission=DUMP"
        }
        val exportedLabBlocks = Regex(
            """<receiver[^>]*android:name="com\.nexivo\.payo\.security\.Lab(?:AdbEscape|HardeningToggle)Receiver"[^>]*>""",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(labDebugText).map { it.value }.toList()
        check(exportedLabBlocks.size >= 2) {
            "Expected LabAdbEscapeReceiver + LabHardeningToggleReceiver in labDebug manifest"
        }
        exportedLabBlocks.forEach { block ->
            check(block.contains(dumpPermission) || block.contains("android.permission.DUMP")) {
                "Exported lab receiver missing DUMP permission: $block"
            }
        }

        listOf(prodDebugManifest to "prodDebug", labReleaseManifest to "labRelease").forEach { (mf, label) ->
            val text = mf.readText()
            labMarkers.forEach { marker ->
                check(!text.contains(marker)) {
                    "$label must not contain lab marker $marker"
                }
            }
        }

        fun allowDoRelease(path: String): Boolean =
            Regex("""ALLOW_DO_RELEASE\s*=\s*(true|false)""")
                .find(file(path).readText())!!
                .groupValues[1]
                .toBoolean()

        check(
            allowDoRelease(
                "build/generated/source/buildConfig/lab/debug/com/nexivo/payo/BuildConfig.java"
            )
        ) { "labDebug ALLOW_DO_RELEASE must be true" }
        check(
            !allowDoRelease(
                "build/generated/source/buildConfig/lab/release/com/nexivo/payo/BuildConfig.java"
            )
        ) { "labRelease ALLOW_DO_RELEASE must be false" }
        check(
            !allowDoRelease(
                "build/generated/source/buildConfig/prod/debug/com/nexivo/payo/BuildConfig.java"
            )
        ) { "prodDebug ALLOW_DO_RELEASE must be false" }

        val apk = file("build/outputs/apk/lab/debug/app-lab-debug.apk")
        check(apk.exists()) { "labDebug APK missing at ${apk.path}" }

        // Cleartext HTTP: labDebug only.
        val labNs = file("src/labDebug/res/xml/network_security_config.xml").readText()
        check(labNs.contains("cleartextTrafficPermitted=\"true\"")) {
            "labDebug must allow cleartext for mock-server"
        }
        val mainNs = file("src/main/res/xml/network_security_config.xml").readText()
        check(mainNs.contains("cleartextTrafficPermitted=\"false\"")) {
            "main/prod network_security_config must deny cleartext"
        }
        val labReleaseNs = file("src/labRelease/res/xml/network_security_config.xml")
        if (labReleaseNs.exists()) {
            check(!labReleaseNs.readText().contains("cleartextTrafficPermitted=\"true\"")) {
                "labRelease must not allow cleartext"
            }
        }
        // Merged resources: labRelease/prodDebug must not ship cleartext=true.
        listOf(
            "build/intermediates/packaged_res/prodDebug/packageProdDebugResources/xml/network_security_config.xml" to "prodDebug",
            "build/intermediates/packaged_res/labRelease/packageLabReleaseResources/xml/network_security_config.xml" to "labRelease"
        ).forEach { (path, label) ->
            val f = file(path)
            if (f.exists()) {
                check(!f.readText().contains("cleartextTrafficPermitted=\"true\"")) {
                    "$label packaged network_security_config must not permit cleartext"
                }
            }
        }

        // No private key material in APK / app sources (lab mock generates at process start).
        val privateKeyMarkers = listOf(
            "BEGIN PRIVATE KEY",
            "BEGIN EC PRIVATE KEY",
            "BEGIN RSA PRIVATE KEY"
        )
        val appScanRoots = listOf(
            file("src"),
            file("build/outputs/apk"),
            file("build/intermediates/merged_assets")
        )
        appScanRoots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile }.forEach labScan@{ f ->
                val name = f.name.lowercase()
                if (name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".der")) {
                    check(false) { "Private/key material file must not ship: ${f.path}" }
                }
                if (f.length() < 2_000_000 && (
                        name.endsWith(".xml") || name.endsWith(".txt") ||
                            name.endsWith(".properties") || name.endsWith(".json")
                        )
                ) {
                    val text = runCatching { f.readText() }.getOrNull() ?: return@labScan
                    privateKeyMarkers.forEach { marker ->
                        check(!text.contains(marker)) {
                            "Private key marker '$marker' found in ${f.path}"
                        }
                    }
                }
            }
        }

        logger.lifecycle(
            "verifyLabAdbEscapePackaging OK (labDebug only; cleartext labDebug-only; no private keys)."
        )
    }
}

gradle.taskGraph.whenReady {
    val runningReleaseTask = allTasks.any { task ->
        val n = task.name
        n.contains("Release") && (
            n.startsWith("assemble") ||
                n.startsWith("bundle") ||
                n.startsWith("package") ||
                n.startsWith("minify") ||
                n.startsWith("shrink")
            )
    }
    if (!runningReleaseTask) return@whenReady

    val serverBaseUrl = configValue("SERVER_BASE_URL")
    if (serverBaseUrl.isNullOrBlank()) {
        throw GradleException(
            "Release build missing SERVER_BASE_URL. " +
                "Set it in local.properties or SERVER_BASE_URL env. See docs/04_Operational_Guides/Release_Build_Config.md."
        )
    }
    if (!serverBaseUrl.trim().lowercase().startsWith("https://")) {
        throw GradleException(
            "Release SERVER_BASE_URL must be https:// (fail closed). Got: " +
                serverBaseUrl.take(32)
        )
    }

    val pinHost = configValue("CERT_PIN_HOST")
    val pinPrimary = configValue("CERT_PIN_SHA256") ?: configValue("CERT_PIN_SHA256_PRIMARY")
    val pinBackup = configValue("CERT_PIN_SHA256_BACKUP")

    if (looksLikePlaceholderHost(pinHost)) {
        throw GradleException(
            "Release build missing/invalid CERT_PIN_HOST. " +
                "Set a real API host (no defaults). See docs/04_Operational_Guides/Release_Build_Config.md."
        )
    }
    if (looksLikePlaceholderPin(pinPrimary)) {
        throw GradleException(
            "Release build missing/invalid CERT_PIN_SHA256 (primary SPKI pin). " +
                "Format: sha256/<base64>. No placeholder defaults. See docs/04_Operational_Guides/Release_Build_Config.md."
        )
    }
    if (looksLikePlaceholderPin(pinBackup)) {
        throw GradleException(
            "Release build missing/invalid CERT_PIN_SHA256_BACKUP (backup SPKI pin). " +
                "Provide a second pin for rotation. See docs/04_Operational_Guides/Release_Build_Config.md."
        )
    }

    val storePath = configValue("STORE_FILE", "STORE_FILE")
    val allowDebugSigned = configValue("ALLOW_DEBUG_SIGNED_RELEASE", "ALLOW_DEBUG_SIGNED_RELEASE")
        ?.equals("true", ignoreCase = true) == true
    if (storePath.isNullOrBlank() || !rootProject.file(storePath).exists()) {
        if (!allowDebugSigned) {
            throw GradleException(
                "Release build missing STORE_FILE keystore (fail closed). " +
                    "Set STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD in local.properties or CI env, " +
                    "or set ALLOW_DEBUG_SIGNED_RELEASE=true only for local minify checks. " +
                    "See docs/04_Operational_Guides/Release_Build_Config.md."
            )
        }
        logger.warn(
            "ALLOW_DEBUG_SIGNED_RELEASE=true — prodRelease will use debug signing (not for distribution)."
        )
    }
}

fun collectProdReleaseScanTexts(): Map<String, String> {
    val out = linkedMapOf<String, String>()
    listOf(file("src/main"), file("build/outputs/apk/prod/release")).filter { it.exists() }.forEach { root ->
        root.walkTopDown().filter { it.isFile }.forEach fileLoop@{ f ->
            val name = f.name.lowercase()
            if (name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".der")) {
                out[f.path] = "BEGIN PRIVATE KEY"
                return@fileLoop
            }
            if (f.length() < 2_000_000 && (
                    name.endsWith(".xml") || name.endsWith(".txt") ||
                        name.endsWith(".properties") || name.endsWith(".json")
                    )
            ) {
                val text = runCatching { f.readText() }.getOrNull() ?: return@fileLoop
                out[f.path] = text
            }
        }
    }
    return out
}

/**
 * Proves prodRelease packaging: no cleartext, real 32-byte SPKI pins, no lab receivers,
 * empty SERVER_API_KEY, no private-key markers, debuggable=false.
 */
tasks.register("verifyProdReleasePackaging") {
    group = "verification"
    description = "Assert prodRelease APK/manifest meet release security packaging rules."
    dependsOn(
        "assembleProdRelease",
        "processProdReleaseMainManifest",
        "generateProdReleaseBuildConfig"
    )
    doLast {
        val manifest = file(
            "build/intermediates/merged_manifest/prodRelease/processProdReleaseMainManifest/AndroidManifest.xml"
        )
        check(manifest.exists()) { "prodRelease merged manifest missing" }
        val buildConfigFile = file(
            "build/generated/source/buildConfig/prod/release/com/nexivo/payo/BuildConfig.java"
        )
        val nsFile = file(
            "build/intermediates/packaged_res/prodRelease/packageProdReleaseResources/xml/network_security_config.xml"
        )
        val nsText = when {
            nsFile.exists() -> nsFile.readText()
            else -> file("src/main/res/xml/network_security_config.xml").readText()
        }
        // AGP may emit app-prod-release.apk (signed) or *-unsigned.apk when using
        // ALLOW_DEBUG_SIGNED_RELEASE / debug signing for local packaging gates.
        val apkDir = file("build/outputs/apk/prod/release")
        val apkExists = apkDir.exists() && apkDir.listFiles()
            ?.any { it.isFile && it.name.startsWith("app-prod-release") && it.name.endsWith(".apk") } == true
        val failures = prodReleasePackagingFailures(
            manifestText = manifest.readText(),
            buildConfigText = buildConfigFile.readText(),
            networkSecurityText = nsText,
            scanFileTexts = collectProdReleaseScanTexts(),
            apkExists = apkExists
        )
        check(failures.isEmpty()) {
            "verifyProdReleasePackaging FAILED:\n" + failures.joinToString("\n") { " - $it" }
        }
        logger.lifecycle(
            "verifyProdReleasePackaging OK (https, 32-byte SPKI pins, no lab classes, no SERVER_API_KEY, cleartext off)."
        )
    }
}

/**
 * Injects one packaging defect at a time into fixture copies; each must make checks FAIL.
 */
tasks.register("verifyProdReleasePackagingNegativeTests") {
    group = "verification"
    description = "Negative tests proving verifyProdReleasePackaging catches injected defects."
    dependsOn("verifyProdReleasePackaging")
    doLast {
        val goodManifest = file(
            "build/intermediates/merged_manifest/prodRelease/processProdReleaseMainManifest/AndroidManifest.xml"
        ).readText()
        val goodBuildConfig = file(
            "build/generated/source/buildConfig/prod/release/com/nexivo/payo/BuildConfig.java"
        ).readText()
        val goodNs = file("src/main/res/xml/network_security_config.xml").readText()
        val goodScan = mapOf("fixture.txt" to "ok")

        data class Case(val name: String, val mutate: () -> List<String>)

        val textPin = "sha256/dGVzdFByaW1hcnlTcGtpUGluVmFsdWVGb3JDaU9ubHk="
        check(looksLikePlaceholderPin(textPin)) {
            "Sanity: documented text/dummy pin must be rejected by looksLikePlaceholderPin"
        }

        val cases = listOf(
            Case("lab_receiver") {
                prodReleasePackagingFailures(
                    manifestText = goodManifest + "\n<!-- LabAdbEscapeReceiver -->\n",
                    buildConfigText = goodBuildConfig,
                    networkSecurityText = goodNs,
                    scanFileTexts = goodScan,
                    apkExists = true
                )
            },
            Case("cleartext_allowed") {
                prodReleasePackagingFailures(
                    manifestText = goodManifest,
                    buildConfigText = goodBuildConfig,
                    networkSecurityText = """<network-security-config><base-config cleartextTrafficPermitted="true" /></network-security-config>""",
                    scanFileTexts = goodScan,
                    apkExists = true
                )
            },
            Case("debuggable_true") {
                prodReleasePackagingFailures(
                    manifestText = goodManifest.replace(
                        "<application",
                        """<application android:debuggable="true""""
                    ),
                    buildConfigText = goodBuildConfig,
                    networkSecurityText = goodNs,
                    scanFileTexts = goodScan,
                    apkExists = true
                )
            },
            Case("dummy_spki_pin") {
                val mutated = goodBuildConfig.replace(
                    Regex("""String CERT_PIN_SHA256_PRIMARY = "[^"]+";"""),
                    """String CERT_PIN_SHA256_PRIMARY = "$textPin";"""
                )
                prodReleasePackagingFailures(
                    manifestText = goodManifest,
                    buildConfigText = mutated,
                    networkSecurityText = goodNs,
                    scanFileTexts = goodScan,
                    apkExists = true
                )
            },
            Case("server_api_key_set") {
                val mutated = goodBuildConfig.replace(
                    Regex("""String SERVER_API_KEY = "[^"]*";"""),
                    """String SERVER_API_KEY = "leaked-key";"""
                )
                prodReleasePackagingFailures(
                    manifestText = goodManifest,
                    buildConfigText = mutated,
                    networkSecurityText = goodNs,
                    scanFileTexts = goodScan,
                    apkExists = true
                )
            },
            Case("pem_marker") {
                prodReleasePackagingFailures(
                    manifestText = goodManifest,
                    buildConfigText = goodBuildConfig,
                    networkSecurityText = goodNs,
                    scanFileTexts = mapOf("evil.pem.txt" to "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBg==\n"),
                    apkExists = true
                )
            }
        )

        val missed = mutableListOf<String>()
        cases.forEach { c ->
            val failures = c.mutate()
            if (failures.isEmpty()) {
                missed += c.name
                logger.error("NEGATIVE TEST MISS: ${c.name} did not fail")
            } else {
                logger.lifecycle("NEGATIVE TEST OK: ${c.name} → ${failures.first()}")
            }
        }
        check(missed.isEmpty()) {
            "verifyProdReleasePackagingNegativeTests missed: $missed"
        }
        logger.lifecycle("verifyProdReleasePackagingNegativeTests OK (6/6 injected defects caught).")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    val knoxSdkJar = file("libs/knoxsdk.jar")
    if (knoxSdkJar.exists()) {
        compileOnly(files(knoxSdkJar))
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Apply Google Services only when google-services.json is present so debug/unit tests
// still compile without Firebase project files. Push falls back to WorkManager polling.
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("google-services.json found — Google Services plugin applied.")
} else {
    logger.lifecycle(
        "google-services.json missing — Google Services plugin skipped. " +
            "FCM push disabled; WorkManager polling remains available."
    )
}
