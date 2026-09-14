# Every core token family, with Unicode to exercise byte/UTF-16 conversion.
@answer 42
def values(item), do: {item, Demo.Item, :ok, :'quoted atom', :==, true, false, nil}
numbers = [1_000, 0b1010, 0o755, 0x2A, 3.14, 1.5e-2]
characters = [?x, ?\n, ?λ, ?🚀]
text = "hello\n#{item}"
words = ~w(one two)a
literal = ~S|literal #{text}|
captured = & &1
result = Demo.run([@answer]) + 2
