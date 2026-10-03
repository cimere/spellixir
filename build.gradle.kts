import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginSignatureTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.time.Duration

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
}

val generatedGrammarRoot = layout.buildDirectory.dir("generated-src/grammar")
val candidateDirectory = layout.buildDirectory.dir("release-candidate")
val candidateArchive = candidateDirectory.map { it.file("candidate.zip") }
val candidateManifest = candidateDirectory.map { it.file("manifest.json") }
val smokeProbeArchive = providers.gradleProperty("smokeProbeArchive")
    .map { layout.projectDirectory.file(it) }
    .orElse(layout.buildDirectory.file("distributions/spellixir-smoke-probe.zip"))
val smokeArchive = providers.gradleProperty("smokeArchive")
    .map { layout.projectDirectory.file(it).asFile }
    .orElse(candidateArchive.map { it.asFile })

tasks.generateParser {
    sourceFile.set(file("src/main/grammar/Elixir.bnf"))
    targetRootOutputDir.set(generatedGrammarRoot)
    pathToParser.set("com/cimere/spellixir/lang/parser/ElixirParser.java")
    pathToPsiRoot.set("com/cimere/spellixir/lang/psi")
    purgeOldFiles.set(true)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.generateParser)
}

sourceSets.main {
    java.srcDir(generatedGrammarRoot)
}

// Test-only plugin: launched alongside an installed candidate ZIP, never included in Spellixir.
val smoke = sourceSets.create("smoke") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}
val smokeProbeJar = tasks.register<Jar>("smokeProbeJar") {
    archiveFileName.set("smoke-probe.jar")
    destinationDirectory.set(layout.buildDirectory.dir("smoke-probe"))
    from(smoke.output) {
        exclude("smoke-plugin.xml", "META-INF/plugin.xml")
    }
    from(checkNotNull(smoke.output.resourcesDir)) {
        include("smoke-plugin.xml")
        rename { "plugin.xml" }
        into("META-INF")
    }
}

val smokeProbeZip = tasks.register<Zip>("smokeProbeZip") {
    archiveFileName.set("spellixir-smoke-probe.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(smokeProbeJar) {
        into("spellixir-smoke-probe/lib")
    }
    dependsOn(smokeProbeJar)
}

val freezeReleaseCandidate = tasks.register<Exec>("freezeReleaseCandidate") {
    group = "distribution"
    description = "Freeze the exact plugin ZIP and packaged smoke probe with SHA-256 provenance."
    dependsOn(tasks.buildPlugin, smokeProbeZip)
    commandLine(
        "python3", "scripts/release_candidate.py", "freeze",
        "--archive", tasks.buildPlugin.flatMap { it.archiveFile }.get().asFile,
        "--probe", smokeProbeArchive.get().asFile,
        "--out", candidateDirectory.get().asFile,
    )
    inputs.file(tasks.buildPlugin.flatMap { it.archiveFile })
    inputs.file(smokeProbeArchive)
    outputs.file(candidateArchive)
    outputs.file(candidateManifest)
}

val verifyReleaseCandidate = tasks.register<Exec>("verifyReleaseCandidate") {
    group = "verification"
    description = "Verify the frozen release candidate hashes, contents, and source commit."
    commandLine("python3", "scripts/release_candidate.py", "verify", "--candidate", candidateDirectory.get().asFile)
    inputs.file(candidateArchive)
    inputs.file(candidateManifest)
}

val signingCertificateFile = layout.buildDirectory.file("signing/certificate-chain.pem")
val signingCertificate = providers.environmentVariable("JB_CERTIFICATE_CHAIN")
val prepareSigningCertificate = tasks.register("prepareSigningCertificate") {
    inputs.property("certificateChain", signingCertificate)
    outputs.file(signingCertificateFile)
    doLast {
        signingCertificateFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(signingCertificate.get())
        }
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, "2026.1.4")
            latest {
                types = listOf(IntelliJPlatformType.IntellijIdea, IntelliJPlatformType.GoLand, IntelliJPlatformType.PyCharm)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "262"
                untilBuild = "262.*"
            }
        }
    }
    signing {
        // Use a file for both signing and verification: the pinned Gradle plugin's
        // inline-certificate verification adds the PEM contents as an extra CLI arg.
        certificateChainFile.set(signingCertificateFile)
        privateKey.set(providers.environmentVariable("JB_PRIVATE_KEY"))
        password.set(providers.environmentVariable("JB_PRIVATE_KEY_PASSWORD"))
    }
    publishing {
        token.set(providers.environmentVariable("JB_MARKETPLACE_TOKEN"))
    }
}

val smokeHost = providers.gradleProperty("smokeHost").orElse("idea").get()
val smokeProduct = when (smokeHost) {
    "idea" -> IntelliJPlatformType.IntellijIdea
    "goland" -> IntelliJPlatformType.GoLand
    "pycharm" -> IntelliJPlatformType.PyCharm
    else -> error("Unsupported smokeHost '$smokeHost'; use idea, goland, or pycharm")
}
// runIde expects a concrete product version; the literal "latest" is not a download version.
val smokeVersion = providers.gradleProperty("smokeVersion").orElse("2026.1.4").map { requested ->
    if (requested == "latest") {
        providers.exec {
            commandLine("python3", "scripts/release_candidate.py", "latest-version", "--host", smokeHost)
        }.standardOutput.asText.get().trim()
    } else requested
}
val smokeWorkDirectory = layout.buildDirectory.dir("packaged-smoke/$smokeHost")
val smokeRuntimeTraps = smokeWorkDirectory.map { it.dir("runtime-traps") }
val smokePluginJar = smokeWorkDirectory.map { it.file("candidate-plugin.jar") }
val extractSmokePlugin = tasks.register<Exec>("extractSmokePlugin") {
    group = "verification"
    dependsOn(verifyReleaseCandidate)
    commandLine("python3", "scripts/release_candidate.py", "extract-plugin-jar",
        "--archive", smokeArchive.get(), "--out", smokePluginJar.get().asFile)
    inputs.file(smokeArchive)
    outputs.file(smokePluginJar)
}
val prepareSmokeRuntimeTraps = tasks.register<Exec>("prepareSmokeRuntimeTraps") {
    group = "verification"
    commandLine("python3", "scripts/release_candidate.py", "prepare-smoke", "--work", smokeWorkDirectory.get().asFile)
}

val packagedSmoke = intellijPlatformTesting.runIde.register("packagedSmoke") {
    type = smokeProduct
    version = smokeVersion.get()
    plugins {
        localPlugin(smokeProbeArchive.get().asFile)
    }
    prepareSandboxTask {
        pluginJar.set(smokePluginJar)
        dependsOn(extractSmokePlugin)
    }
    task {
        timeout.set(Duration.ofMinutes(5))
        sandboxLogDirectory.set(smokeWorkDirectory.map { it.dir("logs") })
        dependsOn(verifyReleaseCandidate, prepareSmokeRuntimeTraps)
        if (!providers.gradleProperty("smokeProbeArchive").isPresent) {
            dependsOn(smokeProbeZip)
        }
        jvmArgs(
            "-Djava.awt.headless=true",
            "-Didea.trust.all.projects=true",
            "-Didea.initially.ask.config=false",
            "-Dide.show.tips.on.startup.default.value=false",
            "-Didea.load.plugins.id=com.cimere.spellixir,com.cimere.spellixir.smoke",
            "-Dspellixir.smoke.host=$smokeHost",
        )
        environment("SPELLIXIR_RUNTIME_ATTEMPT", smokeWorkDirectory.get().file("runtime-attempted").asFile.absolutePath)
        environment("PATH", smokeRuntimeTraps.get().asFile.absolutePath + File.pathSeparator + System.getenv("PATH").orEmpty())
        args(
            "spellixirSmoke",
            smokeWorkDirectory.get().dir("project").asFile.absolutePath,
            smokeWorkDirectory.get().file("smoke.json").asFile.absolutePath,
        )
    }
}

tasks.named<VerifyPluginTask>("verifyPlugin") {
    archiveFile.set(candidateArchive)
    dependsOn(verifyReleaseCandidate)
}

tasks.named<SignPluginTask>("signPlugin") {
    archiveFile.set(candidateArchive)
    signedArchiveFile.set(candidateDirectory.map { it.file("candidate-signed.zip") })
    dependsOn(verifyReleaseCandidate, prepareSigningCertificate)
}

tasks.named<VerifyPluginSignatureTask>("verifyPluginSignature") {
    inputArchiveFile.set(candidateDirectory.map { it.file("candidate-signed.zip") })
    dependsOn("signPlugin")
}

tasks.named<PublishPluginTask>("publishPlugin") {
    archiveFile.set(candidateDirectory.map { it.file("candidate-signed.zip") })
    dependsOn("verifyPluginSignature")
}

tasks.test {
    exclude("**/ElixirResponsivenessTest.class")
    // Native Core fixtures need only Spellixir and its platform dependencies. Loading every
    // bundled IDE plugin also starts unrelated services (including Vue's language server).
    systemProperty("idea.load.plugins.id", "com.cimere.spellixir")
    systemProperty("spellixir.corpus.dir", layout.projectDirectory.dir("src/test/resources/corpus/phase1").asFile.absolutePath)
    systemProperty("spellixir.corpus.report", layout.buildDirectory.dir("reports/syntax-corpus").get().asFile.absolutePath)
    inputs.dir(layout.projectDirectory.dir("src/test/resources/corpus/phase1"))
    outputs.dir(layout.buildDirectory.dir("reports/syntax-corpus"))
}

intellijPlatformTesting.testIde.register("verifyNativeCoreResponsiveness") {
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    task {
        description = "Measure large-file Native Core parsing and incremental editor updates."
        classpath += sourceSets.test.get().runtimeClasspath
        include("**/ElixirResponsivenessTest.class")
        // Avoid competing with the other verification suites when running check.
        mustRunAfter(tasks.test, "verifySyntaxCorpus")
        systemProperty("idea.load.plugins.id", "com.cimere.spellixir")
        systemProperty("spellixir.responsiveness.report", layout.buildDirectory.file("reports/responsiveness/results.json").get().asFile.absolutePath)
        outputs.upToDateWhen { false }
        timeout.set(Duration.ofMinutes(5))
    }
}

tasks.register<Exec>("verifySyntaxCorpus") {
    group = "verification"
    description = "Compare the Native Core corpus with pinned official Tree-sitter sources (requires Python 3 and a C compiler)."
    dependsOn(tasks.test)
    commandLine("python3", "scripts/verify_syntax_corpus.py")
}

tasks.check {
    dependsOn("verifySyntaxCorpus")
    dependsOn("verifyNativeCoreResponsiveness")
}

group = "com.cimere.spellixir"
version = "0.1.7"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2026.1.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.cimere.spellixir"
        name = "Spellixir"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "261.26222.65"
            untilBuild = "262.*"
        }
    }
}
