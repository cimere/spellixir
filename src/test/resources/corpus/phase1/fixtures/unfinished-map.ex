def unfinished(value), do: %{status: :ok,
def complete(value), do: {:ok, value}
