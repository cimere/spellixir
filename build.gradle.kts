import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
}

val generatedGrammarRoot = layout.buildDirectory.dir("generated-src/grammar")

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
