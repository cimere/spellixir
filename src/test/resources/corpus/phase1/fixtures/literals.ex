name = "world"
message = "hello #{name}"
charlist = 'hello #{name}'
heredoc = """
hello #{name}
"""
char_heredoc = '''
hello #{name}
'''
regex = ~r/foo #{name}/iu
literal = ~S|literal #{name}|
custom = ~j<{"name": "#{name}"}>u
