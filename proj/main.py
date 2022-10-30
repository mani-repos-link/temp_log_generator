from parsers.declare import DeclareParser

# with open("../files/reference10.decl") as file:

with open("../files/Response.decl") as file:
    d2a = DeclareParser(file.read())
    d2a.parse()

