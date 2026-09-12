package com.cimere.spellixir.mix

import org.junit.Assert.assertEquals
import org.junit.Test

class MixMetadataReaderTest {
    @Test
    fun recognizesConventionalOrdinaryAndUmbrellaDefinitions() {
        assertEquals(MixMetadata.Ordinary, MixMetadataReader.read(mixDefinition(
            "app: :demo, version: \"0.1.0\", start_permanent: Mix.env() == :prod, deps: deps()",
            "defp deps do [{:sample, \"~> 1.0\"}] end",
        )))
        assertEquals(MixMetadata.Umbrella(listOf("apps")), MixMetadataReader.read(mixDefinition(
            "apps_path: \"apps\", version: \"0.1.0\", deps: []",
        )))
        assertEquals(MixMetadata.Umbrella(listOf("components", "elixir")), MixMetadataReader.read(
            mixDefinition("apps_path: \"components/elixir\""),
        ))
    }

    @Test
    fun supportsZeroArityParenthesesAndInlineProjectDefinitions() {
        val source = "defmodule Demo do use Mix.Project def project(), do: [app: :demo] end"
        assertEquals(MixMetadata.Ordinary, MixMetadataReader.read(source))
    }

    @Test
    fun ignoresMisleadingNestedKeysStringsCommentsAndOtherFunctions() {
        val source = mixDefinition(
            "app: :demo, description: \"apps_path: fake\", docs: [apps_path: \"fake\"] # apps_path: \"fake\"",
            "defp other do [apps_path: \"fake\"] end",
        ).replace("# apps_path: \"fake\"]", "# apps_path: \"fake\"\n]")
        assertEquals(MixMetadata.Ordinary, MixMetadataReader.read(source))
        assertEquals(MixMetadata.Unknown, MixMetadataReader.read("# " + mixDefinition("app: :demo").replace("\n", "\n# ")))
    }

    @Test
    fun containsMalformedAndUnsupportedMetadata() {
        for (source in listOf(
            "", "raise \"not a project\"", "[apps_path: \"apps\"]",
            mixDefinition("app: ???"), mixDefinition("app: :demo").dropLast(3),
            mixDefinition("app: :demo, apps_path: \"apps\"" ).replace("]", "}"),
            mixDefinition("app: :demo, app: :other"),
            mixDefinition("app: :demo") + " garbage",
            mixDefinition("app: :demo").replace("use Mix.Project", "use Other.Project"),
            mixDefinition("app: :demo").replace("def project", "defp project"),
            mixDefinition("app: :demo").replace("def project", "def project(arg)"),
            mixDefinition("app: :demo", "def project do [app: :other] end"),
            mixDefinition("apps_path: System.get_env(\"APPS\")"),
            mixDefinition("apps_path: \"#{path()}\""),
            mixDefinition("apps_path: \"apps\" <> suffix()"),
            mixDefinition("apps_path: \"apps\", apps: [:only_one]"),
        )) assertEquals(source, MixMetadata.Unknown, MixMetadataReader.read(source))
    }

    @Test
    fun rejectsPathsOutsideSupportedRelativeLayout() {
        for (path in listOf("", "/apps", "../apps", "a/../apps", "./apps", "a//apps", "C:/apps", "a\\apps", "apps/")) {
            assertEquals(path, MixMetadata.Unknown, MixMetadataReader.read(mixDefinition("apps_path: \"$path\"")))
        }
    }

    @Test
    fun containsExcessiveSizeAndNesting() {
        assertEquals(MixMetadata.Unknown, MixMetadataReader.read(" ".repeat(MixMetadataReader.MAX_BYTES + 1)))
        assertEquals(MixMetadata.Unknown, MixMetadataReader.read(mixDefinition("app: :demo, data: " + "[".repeat(1000) + "]".repeat(1000))))
    }
}
