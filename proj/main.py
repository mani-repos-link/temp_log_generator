from parsers.declare import DECLARE2LP

# with open("../files/reference10.decl") as file:
with open("../files/Response.decl") as file:
    d2a = DECLARE2LP(file.read())
    d2a.parse()
