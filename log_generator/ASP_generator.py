from datetime import datetime

import clingo
from clingo import SymbolType
import pm4py
from pm4py.objects.log import obj as lg
import pandas as pd

from abstracts.log_generator import Log_generator
from alp import DECLARE2LP
from parsers.declare.declare import DeclareParser


class ASP_generator(Log_generator):

    def __init__(self,
                 num_traces: int, min_event: int, max_event: int,
                 decl_model_path: str, template_path: str, encoding_path: str):
        super().__init__(num_traces, min_event, max_event)
        self.decl_model_path = decl_model_path
        self.template_path = template_path
        self.encoding_path = encoding_path
        self.trace_xes: [lg.Trace] = []

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
        ctl = clingo.Control([f"-c t={self.num_traces}", "1"])  # TODO: add parameters
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
        result = output.symbols(shown=True)
        print(result)
        traced = {}
        for m in result:  # create dict
            trace_name = str(m.name)
            arg_len = len(m.arguments)
            l, i = ([], 0)
            for arg in m.arguments:  # resources
                i = i + 1
                if arg.type == SymbolType.Function:
                    l.append(str(arg.name))
                if arg.type == SymbolType.Number:
                    num = str(arg.number)
                    l.append(num)
                    if i == arg_len:
                        if num not in traced:
                            traced[num] = {}
                        traced[num][trace_name] = l

        print(traced)
        lines = []
        for trace_id in range(len(traced)):
            line = {'case_id': trace_id}
            for i in traced:
                trace = traced[i]
                event_name = trace["trace"][0]
                line['case:concept:name'] = event_name
                e = {i: trace[i] for i in trace if i != 'trace'}  # filter trace by removing trace key
                for i in e:  # e = {'assigned_value': ['grade', '5', '1']}
                    line['concept:name'] = e[i][0]
                    line["time:timestamp"] = datetime.now().timestamp()  # + timedelta(hours=c).datetime
            lines.append(line)

        dt = pd.DataFrame(lines)
        dt = pm4py.format_dataframe(dt, case_id='case_id', activity_key='concept:name',
                                           timestamp_key='time:timestamp')
        logger = pm4py.convert_to_event_log(dt)
        pm4py.write_xes(logger, 'logy.xes')

