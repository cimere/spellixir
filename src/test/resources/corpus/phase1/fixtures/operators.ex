def compare(a, b) when a in [1, 2], do: not (a == b) and a != b or a <= b
result = value |> Demo.run()
power = 2 ** 3
range = 1..10//2
list = [1] ++ [2] -- [3]
binary = <<1, 2>>
bits = a <<< 2 ||| b &&& c ^^^ d
