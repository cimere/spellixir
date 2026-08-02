defmodule SpellixirExpertFixture.Sample do
  @moduledoc false

  def greeting(name) do
    String.upcase(name)
  end

  def diagnostic_fixture do
    definitely_missing_function()
  end
end
