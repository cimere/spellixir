defmodule SpellixirExpertFixture.Sample do
  @moduledoc false

  def greeting(name) do
    normalize(name)
  end

  defp normalize(name), do: String.upcase(name)

  def diagnostic_fixture do
    definitely_missing_function()
  end
end
