import sys
from typing import Union

import clingo
import json

from clingo import SolveHandle, SolveResult, SolveControl


class ExampleApp:

    def handle_output(self, output: clingo.solving.Model):
        filename = "clingo_output.asp"
        sc: SolveControl = output.context
        fl = []
        print(output.__dict__)
        for x in sc.symbolic_atoms:
            # True            False         364        [String('Succession'), Function('s_1', [], True)
            # print(x.is_fact, x.is_external, x.literal, x.symbol.negative)
            fl.append(x.symbol.__str__())
        with open(filename, "w+") as f:
            f.write("\n".join(fl))
        # print("file generated", filename)
        print(output)

    def handle_output2(self, output: clingo.solving.Model):
        for x in output.context.symbolic_atoms:
            # print(x)
            pass
    #   TODO: pm4py

    def run(self):
        ctl = clingo.Control(["-c t=5", "1"])
        ctl.load("files/generation_encoding.lp")
        ctl.load("files/templates.lp")
        ctl.load("generation_instance.lp")
        ctl.ground([("base", [])], context=self)
        # out: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output)
        im: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output2)
        print(json.dumps(ctl.statistics, indent=4))
        # print(im)
        # im: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output)
        # print(im)
        # im: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output)
        # print(im)


if __name__ == "__main__":
    ExampleApp().run()

