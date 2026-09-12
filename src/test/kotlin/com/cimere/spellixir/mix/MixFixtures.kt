package com.cimere.spellixir.mix

internal fun mixDefinition(configuration: String, functions: String = "") = """
    defmodule Demo.MixProject do
      use Mix.Project
      def project do
        [$configuration]
      end
      $functions
    end
""".trimIndent()
