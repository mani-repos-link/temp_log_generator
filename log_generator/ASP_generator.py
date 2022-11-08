from abstracts.log_generator import Log_generator
import tempfile
import clingo

from log_generator.alp import DECLARE2LP
from log_generator.parsers.declare.declare import DeclareParser


class ASP_generator(Log_generator):

    def __init__(self,
                 num_traces: int, min_event: int, max_event: int,
                 decl_model_path: str, template_path: str, encoding_path: str):
        super().__init__(num_traces, min_event, max_event)
        self.decl_model_path = decl_model_path
        self.template_path = template_path
        self.encoding_path = encoding_path

    def __decl_model_to_lp_file(self):
        with open(self.decl_model_path, "r") as file:
            d2a = DeclareParser(file.read())
            dm = d2a.parse()
            lp_model = DECLARE2LP().from_decl(dm)
            lp = lp_model.__str__()
        # with tempfile.NamedTemporaryFile() as tmp:
        with open("generated.lp", "w+") as tmp:
            tmp.write(lp)
        return "generated.lp"

    def run(self):
        decl2lp_file = self.__decl_model_to_lp_file()
        ctl = clingo.Control(["-c t=5", "1"])  # TODO: add parameters
        ctl.load(self.encoding_path)
        ctl.load(self.template_path)
        # ctl.load("generation_instance.lp")
        ctl.load(decl2lp_file)
        ctl.ground([("base", [])], context=self)
        out = ctl.solve(on_model=self.__handle_clingo_result)
        print(out)
        # print(ctl.statistics)
        # im: Union[SolveHandle, SolveResult] = ctl.solve(on_model=self.handle_output2)

    def __handle_clingo_result(self, output: clingo.solving.Model):
        print(output)
