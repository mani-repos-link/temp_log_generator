from typing import Union

import clingo
import json

from clingo import SolveHandle, SolveResult, SolveControl


class ExampleApp:

    def handle_output(self, output: clingo.solving.Model):
        print(output)
        filename = "clingo_output.asp"
        sc: SolveControl = output.context
        fl = []
        for x in sc.symbolic_atoms:
            # True            False         364        [String('Succession'), Function('s_1', [], True)
            print(x.is_fact, x.is_external, x.literal, x.symbol.negative)
            fl.append(x.symbol.__str__())
        with open(filename, "w+") as f:
            f.write("\n".join(fl))
        print("file generated", filename)

    def run(self):
        ctl = clingo.Control()
        # TODO: find a way to add generation_encoding.lp, templates.lp, generation_instance.lp(translated from declare model)
        # ctl.load("files/generation_encoding.lp")
        # ctl.load("files/templates.lp")
        ctl.load("generation_instance.lp")
        # ctl.load("files/reference10.lp")
        # ctl.load("generation_instance.lp")
        # ctl.load("files/generation_encoding.lp")
        ctl.ground([("base", [])], context=self)
        # out: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output)
        im: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output)
        print(im)

if __name__ == "__main__":
    ExampleApp().run()

