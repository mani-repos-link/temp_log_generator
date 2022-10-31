from parsers.declare import DECLARE2LP, DeclareParser

# with open("../files/reference10.decl") as file:

with open("../files/Response.decl") as file:
    d2a = DeclareParser(file.read())
    dm = d2a.parse()
    lp = DECLARE2LP()
    s = lp.from_decl(dm)
    print(s)

