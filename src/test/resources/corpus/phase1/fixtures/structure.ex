alias Demo.Repo
defmodule Demo.Service do
  def fetch({:user, user_id}, options) do
    Repo.get(User, user_id)
    notify options
    [user_id]
    %{status: :ok}
    (user_id + 1)
    {:ok, user_id, &1, &Repo.get/1}
  end
end
