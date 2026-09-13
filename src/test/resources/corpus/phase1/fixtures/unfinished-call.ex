def unfinished(value), do: Repo.get(value,
def complete(value), do: {:ok, value}
