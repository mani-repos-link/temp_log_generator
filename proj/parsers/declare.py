from __future__ import annotations

import warnings
from enum import Enum
import typing
from json import JSONEncoder
import json
import re
import boolean

"""
Declare Model Syntax


[exmample.decl]

activity A                                  # defining an Object/Event.
bind A: grade                               # defining an attribute or property. Line starting with "bind" word
bind A: mark, name                          # defining multiple attributes or properties together
activity B
bind B: grade, mark, name
grade, mark: integer between 1 and 5        # multiple props/attrs declaration as far as they share the same declaration type. the types can be: integer, float, enumeration
# mark: float between 2 and 9.5             # start '#' char we can consider line as comment
name: x, y, z, v                            # data declaration as enumeration 

# constraints template are pre-defined and after template name defined, it can have also 3 or
# 2 pipes( | | | ) which defines constraints
Response[A, B] |A.grade = 3 |B.grade > 5 |1,5,s
Response[A, B] |A.grade <= 4 | A.name is y | 0,s

---
        
An object or Event can be defined in the declare(.decl) using a standalone line 
and specifying
 `[typeOfObjectOrEvent] [nameInUppercase]`
for example as we can see above in the example
 `activity A`

An Object/Event can has/have also properties:
 `bind [nameOfObject]: [newPropertyName]`
 I.e in above example
 `bind A: grade`
line starting from "bind" used to create new attributes or properties.(A.mark) 


Declare has some predefined Constraint Templates and some of them are described here in this document on page 7:
@article{maggidata,
  title={Data-aware Synthetic Log Generation for De-clarative Process Models},
  author={Maggi, Fabrizio Maria and Di Francescomarino, Chiara and Ghidini, Chiara}
}

Declaration can have types such as: integer, float, enumeration for now.
Moreover, integer and float can have ranges defined as  `integer between 5 and 9` or `float between 2.9 and 6`.
A label can be check in constraint using `is` keyword and can
be negate `is not`. ie. `template[a,b] |a is x| b is not x |1,2,s`



SUGGESTIONS OR DOUBTS
 - comment lines starting from `#`
 - have some reserve words
 


"""


class DECLARE_RESERVED:
    words = ["is", "not", "in", "between", "integer", "float", "enumeration", "and"]
    condition_symbols = [">", "<", "=", "is", ">=", "<=", "is not", "in", "not"]


class DECLARE_LINE_DEFINITION(Enum):
    DEFINE_EVENT_LINE = 1
    DEFINE_PROP_LINE = 2  # TODO: fix name
    DEFINE_PROP_VALUE_LINE = 3  # Constraints
    DEFINE_CONSTRAINT_TEMPLATE = 4


class DeclarePropertyValueType(Enum):
    INTEGER = "integer"
    FLOAT = "float"
    ENUMERATION = "enumeration"


class DeclareObjectPropertyType(JSONEncoder):
    typ: DeclarePropertyValueType = None
    is_range_typ: bool = None
    value: str | typing.List[str] = None

    def __init__(self, typ: DeclarePropertyValueType = None, is_range_typ: bool = None,
                 value: str | Enum | typing.List[str] = None):
        self.typ = typ
        self.is_range_typ = is_range_typ
        self.value = value

    def __iter__(self):
        yield from {
            "typ": self.typ,
            "is_range_typ": self.is_range_typ,
            "value": self.value
        }.items()

    def __dict__(self):
        return {"typ", self.typ}

    def __repr__(self):
        return self.__str__()

    def __str__(self):
        return f"""{{"typ": {self.typ or "null"},"is_range_typ": {self.is_range_typ or "null"},"value": {self.value or "null"}}}"""
        # return json.dumps(self, ensure_ascii=False)

    def to_json(self):
        return self.__dict__()

    def default(self, o: typing.Any) -> typing.Any:
        return o.__dict__

    def encode(self, o: typing.Any) -> str:
        return self.__str__()


class ConstraintTemplates:
    template_name: str
    events_list: typing.List[str]
    conditions: str
    active_cond: str
    active_cond_parsed: typing.Dict[str, str]
    correlation_cond_parsed: typing.Dict[str, str]
    correlation_cond: str
    ts: str

    def __repr__(self):
        return self.__str__()

    def __str__(self):
        return f"{{\"name\": {self.template_name}, \"events\": {self.events_list}, \"conditions\": {self.conditions}, " \
               f"\"active_cond\": {self.active_cond}, \"correlation_cond\": {self.correlation_cond}, \"ts\": {self.ts} }}"


class DeclareModel:
    obj: dict[str, typing.Dict[str, typing.Dict[str, DeclareObjectPropertyType] | str]] = {}
    # obj: dict = {}
    props: dict[str, typing.List[DeclareObjectPropertyType]] = {}
    templates: typing.List[ConstraintTemplates] = []
    templates_dict: typing.Dict[str, typing.List[ConstraintTemplates]] = {}
    # typing.Dict[str, Enum | str | typing.List[str]]
    # {
    #     "A": {
    #         "object_type": "activity"
    #         "props": {
    #           "mark": {
    #               "typ": "integer",
    #               "is_range_typ": True,
    #               "val": "between 1 and 5"
    #           },
    #           "grade": {
    #               "typ": "float",
    #               "is_range_typ": True,
    #               "val": "between 1 and 5"
    #           }
    #        }
    #     }
    # }


class DeclareConstraintConditionResolver:

    def parsed_condition(self, condition: typing.Literal['activation', 'correlation'], string: str):
        string = re.sub('\)', ' ) ', string)
        string = re.sub('\(', ' ( ', string)
        string = string.strip()
        string = re.sub(' +', ' ', string)
        string = re.sub('is not', 'is_not', string)
        string = re.sub('not in', 'not_in', string)
        string = re.sub(' *> *', '>', string)
        string = re.sub(' *< *', '<', string)
        string = re.sub(' *= *', '=', string)
        string = re.sub(' *<= *', '<=', string)
        string = re.sub(' *>= *', '>=', string)
        form_list = string.split(" ")

        for i in range(len(form_list) - 1, -1, -1):
            el = form_list[i]
            if el == 'in' or el == 'not_in':
                end_index = form_list[i:].index(')')
                start_index = i - 1
                end_index = end_index + i + 1
                form_list[start_index:end_index] = [' '.join(form_list[start_index:end_index])]
            elif el == 'is' or el == 'is_not':
                start_index = i - 1
                end_index = i + 2
                form_list[start_index:end_index] = [' '.join(form_list[start_index:end_index])]

        for i in range(len(form_list)):
            el = form_list[i]
            if '(' in el and ')' in el:
                el = re.sub('\( ', '(', el)
                el = re.sub(', ', ',', el)
                el = re.sub(' \)', ')', el)
                form_list[i] = el

        keywords = {'and', 'or', '(', ')'}
        c = 0
        name_to_cond = dict()
        cond_to_name = dict()
        for el in form_list:
            if el not in keywords:
                c = c + 1
                name_to_cond[condition + '_condition_' + str(c)] = el
                cond_to_name[el] = condition + '_condition_' + str(c)
        form_string = ''
        for el in form_list:
            if el in cond_to_name:
                form_string = form_string + cond_to_name[el] + ' '
            else:
                form_string = form_string + el + ' '

        algebra = boolean.BooleanAlgebra()
        expression = algebra.parse(form_string, simplify=True)
        return expression, name_to_cond, cond_to_name

    def tree_conditions_to_asp(self, condition: typing.Literal['activation', 'correlation'],
                               expression, cond_name: str, i, conditions_names,
                               lp_st=None) -> typing.List[str] | None:
        if lp_st is None:
            lp_st = []

        def expression_to_name(expression):
            if expression.isliteral:
                condition_name = str(expression)
            else:
                condition_name = condition + '_condition_' + ''.join(
                    [str(symbol).split('_')[2] for symbol in expression.get_symbols()])
                while condition_name in conditions_names:
                    condition_name = condition_name + '_'
                conditions_names.add(condition_name)
            return condition_name + '({},T)'.format(i)

        def no_params(arg_name):
            return arg_name.split('(')[0]

        if expression.isliteral:
            return
        cond_name = cond_name + '({},T)'.format(i)
        formula_type = expression.operator
        formula_args = expression.args
        if formula_type == '|':
            for arg in formula_args:
                arg_name = expression_to_name(arg)
                lp_st.append('{} :- {}.'.format(cond_name, arg_name))
                self.tree_conditions_to_asp(condition, arg, no_params(arg_name), i, conditions_names, lp_st)
        if formula_type == '&':
            args_name = ''
            for arg in formula_args:
                arg_name = expression_to_name(arg)
            args_name = args_name[:-1]  # remove last comma
            lp_st.append('{} :- {}.'.format(cond_name, args_name))
            for arg in formula_args:  # breadth-first (è più costoso della depth ma è più elegante, è la stessa della disgiunzione)
                arg_name = expression_to_name(arg)
                self.tree_conditions_to_asp(condition, arg, no_params(arg_name), i, lp_st)
        return lp_st



class DeclareParser:
    CONSTRAINTS_TEMPLATES_PATTERN = "^(.*)\[(.*)\]\s*(.*)$"
    CONSTRAINTS_TEMPLATES = {
        "Init($,$)": {"argsNum": 1, "semantic": "First task is A"},
        "Existence($) ": {"argsNum": 1, "semantic": "Task A should be executed"},
        "Existence($,$)": {"argsNum": 2, "semantic": "Task A should be executed N or more times (N is number)"},
        "Absence($)": {"argsNum": 1, "semantic": "Task A should not be executed"},
        "Absence($,$)": {"argsNum": 2, "semantic": "Task A may be executed N times or less"},
        "Exactly($,$)": {"argsNum": 2, "semantic": "Task A should be executed (exactly) N times"},
        "Choice($,$)": {"argsNum": 2, "semantic": "Task A or task B should be executed (or both)"},
        "ExclusiveChoice($,$)": {"argsNum": 2, "semantic": "Task A or task B should be executed, but not both"},
        "RespondedExistence($,$)": {"argsNum": 2, "semantic": "If task A executed, task B executed as well"},
        "Response($,$)": {"argsNum": 2, "semantic": "If task A executed, task B executed after A"},
        "AlternateResponse($,$)": {"argsNum": 2, "semantic": "If task A executed, task B executed after A, without "
                                                             "other A in between "},
        "ChainResponse($,$)": {"argsNum": 2, "semantic": "If task A executed, task B executed next"},
        "Precedence($,$)": {"argsNum": 2, "semantic": "If task A executed, task B was executed before A"},
        "AlternatePrecedence($,$)": {"argsNum": 2, "semantic": "If task A executed, task B was executed before A, "
                                                               "without other A in between"},
        "ChainPrecedence($,$)": {"argsNum": 2, "semantic": "If task A executed, previous executed task was B"},
        "NotRespondedExistence($,$)": {"argsNum": 2, "semantic": "If task A executed, task B is not executed"},
        "NotResponse($,$)": {"argsNum": 2, "semantic": "If task A executed, task B will not be executed after A"},
        "NotPrecedence($,$)": {"argsNum": 2, "semantic": "If task A executed, task B was not executed before A"},
        "NotChainResponse($,$)": {"argsNum": 2, "semantic": "If task A executed, task B is not executed next"},
        "NotChainPrecedence($,$)": {"argsNum": 2, "semantic": "If task A executed, previous executed task was not B"},
    }
    declare_content: [str]
    model: DeclareModel
    acts = {}  # { act_name: [] }

    def __init__(self, content: typing.Union[str, typing.List[str]]):
        self.load_declare_content(content)
        self.model = DeclareModel()

    def load_declare_content(self, content: typing.Union[str, typing.List[str]]):
        if isinstance(content, str):
            self.declare_content = content.split('\n')
        else:
            self.declare_content = content
        self.model = DeclareModel()

    def parse(self):
        line_index = 0
        for line in self.declare_content:
            line = line.strip('\n').strip()  # clear the string, removing whitespace
            line_index = line_index + 1
            if len(line) > 0 and not line.startswith("#"):
                self.detect_line(line, line_index)
        # print(self.model.obj)
        # dop = DeclareObjectPropertyType(value="da1")
        # with open("ftes.txt", "w+") as f:
        #     json.dump( dop, f)
        # print(json.dumps(self.model.obj))
        return self.model

    def detect_line(self, line: str, line_idx: int):
        line = line.strip()
        if self.is_object_definition(line):
            self.__parse_object_definition(line, line_idx)
        elif self.is_object_prop_definition(line):
            self.__parse_object_prop_definition(line, line_idx)
        elif self.is_object_prop_value_definition(line):
            self.__parse_object_prop_value_definition(line, line_idx)
        elif self.is_constraint_template_definition(line):
            self.__parse_constraint_template(line, line_idx)
        else:
            raise ValueError(f"Unable to parse the line[{line_idx}]")

    def is_object_definition(self, line: str) -> bool:
        x = re.search("^[\w]+ [\w]+$", line, re.MULTILINE)
        return x is not None

    def is_object_prop_definition(self, line: str) -> bool:
        x = re.search("^bind [a-zA-Z_]+[0-9]* *: *[\w, ]+$", line, re.MULTILINE)
        return x is not None

    def is_object_prop_value_definition(self, line: str) -> bool:
        x = re.search("^(?!bind)[a-zA-Z_, ]+[0-9]* *: *[\w, ]+", line, re.MULTILINE)
        return x is not None

    def is_constraint_template_definition(self, line: str) -> bool:
        x = re.search(self.CONSTRAINTS_TEMPLATES_PATTERN, line, re.MULTILINE)
        return x is not None

    def __parse_object_definition(self, line: str, line_idx: int):
        var_declaration: typing.List[str] = line.split(' ')
        if len(var_declaration) != 2:
            raise ValueError(f"Error in line {line_idx}: {line}.\n\tCan have only two words for defining an event: "
                             f"`EventType EventName`")
        obj_name = var_declaration[1].strip()
        typ_obj = var_declaration[0].strip()
        if obj_name in self.model.obj:
            raise KeyError(f"Error in line {line_idx}: {line}.\n\tMultiple times declared {obj_name}")
        if self.__is_reserved_keyboard(obj_name):
            raise NameError(f"""`{obj_name}` reserved object name used.""")
        if self.__is_reserved_keyboard(typ_obj):
            raise NameError(f"""Type of object defined(`{typ_obj}`) is already reserved.""")
        self.model.obj[obj_name] = {"object_type": typ_obj}

    def __parse_object_prop_definition(self, line: str, line_idx: int):
        arr = line.replace('bind', '').strip().split(':')  # LINE: bind B: grade, mark, name
        obj_name = arr[0].strip()  # B
        propsOrAttrs = arr[1].strip().split(',')  # grade, mark, name
        # propsOrAttrs = [p.strip() for p in propsOrAttrs]
        if obj_name not in self.model.obj:
            raise NameError(f"""Error in line: {line_idx}""")
        obj = self.model.obj[obj_name]
        if "props" not in obj:
            obj["props"]: typing.Dict[str, DeclareObjectPropertyType] = {}
        props = obj["props"]
        for p in propsOrAttrs:
            p = p.strip()
            if self.__is_reserved_keyboard(p):
                raise NameError(f"""Type of object property defined(`{p}`) is already reserved.""")
            props[p] = DeclareObjectPropertyType()
            if p in self.model.props:
                sp = self.model.props[p]
                sp.append(props[p])
            else:
                self.model.props[p] = [props[p]]

    def __parse_object_prop_value_definition(self, line: str, line_idx: int):
        arr = line.strip().split(':')  # grade, mark: integer between 1 and 5
        if len(arr) != 2:
            raise ValueError(f"Failed to parse in line {line_idx}: {line}")
        props = arr[0].strip().split(",")
        value = arr[1].strip()
        dopt = self.__parse_attr_value(value, line_idx)
        for p in props:
            p = p.strip()
            if p not in self.model.props:
                warnings.warn(f""" "{p}" attribute not defined. Found in line[{line_idx}] "{line}" """)
                continue
            props_of_obj = self.model.props[p]
            if props_of_obj:
                for pr in props_of_obj:
                    pr.typ = dopt.typ
                    pr.is_range_typ = dopt.is_range_typ
                    pr.value = dopt.value

    def __parse_attr_value(self, value: str, idx: int) -> DeclareObjectPropertyType:
        # value: integer between 1 and 5           #  <--- integer
        # value: float between 1 and 5             #  <--- float
        # value: x, y, z, v                        #  <--- enumeration
        integer_range_rx = "^integer[ ]+between[ ]+[+-]?\d+[ ]+and[ ]+[+-]?\d$"
        float_range_rx = "^float[ ]+between[ ]+[+-]?\d+(\.\d+)?[ ]+and[ ]+[+-]?\d+(\.\d+)?$"
        enume_rx = "^[\w]+(,[ ]*[\w.]+)*$"  # matches -> [xyz, xyz, dfs], [12,45,78.54,454]
        value = value.strip()
        integer = re.search(integer_range_rx, value, re.MULTILINE)
        flt = re.search(float_range_rx, value, re.MULTILINE)
        enume = re.search(enume_rx, value, re.MULTILINE)
        dopt = DeclareObjectPropertyType()
        dopt.value = value
        if integer:
            dopt.typ = DeclarePropertyValueType.INTEGER
            dopt.is_range_typ = True
        elif flt:  # float
            dopt.typ = DeclarePropertyValueType.FLOAT
            dopt.is_range_typ = True
        elif enume:
            dopt.typ = DeclarePropertyValueType.ENUMERATION
            dopt.is_range_typ = False
            # dopt.value = value.split(",")
        else:
            x = re.search("^[+-]?\d+$", value, re.MULTILINE)
            if x:
                dopt.typ = DeclarePropertyValueType.INTEGER
                dopt.is_range_typ = False
            elif re.search("^[+-]?\d+(\.\d+)?$", value, re.MULTILINE):
                dopt.typ = DeclarePropertyValueType.FLOAT
                dopt.is_range_typ = False
            else:
                raise ValueError(f"""Unable to parse {value}""")
        return dopt

    def __parse_constraint_template(self, line: str, line_idx: int):
        # Response[A, B] |A.grade = 3 |B.grade > 5 |1,5,s
        compiler = re.compile(self.CONSTRAINTS_TEMPLATES_PATTERN)
        al = compiler.fullmatch(line)
        tmp_name = al.group(1).strip()  # template names: Response, Existence...
        events = al.group(2).strip().split(",")  # A, B
        events = [e.strip() for e in events]  # [A, B]
        conditions = al.group(3)  # |A.grade = 3 |B.grade > 5 |1,5,s
        keys = self.model.obj.keys()
        for ev_nm in events:
            ev_nm = ev_nm.strip()
            if ev_nm not in keys:
                raise ValueError(f"{ev_nm} attribute is not defined. Failed in line[{line_idx}]")
        ct = ConstraintTemplates()
        ct.template_name = tmp_name
        ct.events_list = events
        ct.conditions = conditions
        conds_list = conditions.strip().strip("|").split("|")
        # TODO: ask whether the C.T with one Event can have multiple conditions?
        # TODO: Existence[A] | ...| ... | ... |
        # if len(events) == 1:
        #     conds_list
        conds_len = len(conds_list)

        if conds_len == 0:
            pass
        if conds_len == 1:
            ct.active_cond = conds_list[0]
            ct.active_cond_parsed = self.__parse_constraints_cond(ct.active_cond)
        elif conds_len == 2:
            ct.active_cond = conds_list[0]
            ct.correlation_cond = conds_list[1]
            ct.active_cond_parsed = self.__parse_constraints_cond(ct.active_cond)
            ct.correlation_cond_parsed = self.__parse_constraints_cond(ct.correlation_cond)
        elif conds_len == 3:
            ct.active_cond = conds_list[0]
            ct.correlation_cond = conds_list[1]
            ct.active_cond_parsed = self.__parse_constraints_cond(ct.active_cond)
            ct.correlation_cond_parsed = self.__parse_constraints_cond(ct.correlation_cond)
            ct.ts = conds_list[2]
        else:
            # TODO: what to in this case
            raise ValueError(f"Unable to parse the line {line} due to the exceeds conditions (> 3)")
        self.model.templates.append(ct)
        lis = []
        if tmp_name in self.model.templates_dict:
            lis = self.model.templates_dict
        else:
            self.model.templates_dict[tmp_name] = lis
        lis.append(ct)

    def __parse_constraints_cond(self, cond1: str) -> dict[str, str]:
        cond = cond1.strip()
        cond = ' '.join(cond.split())
        cond_parser_group_reg = "^([\w]+.[\w]+)[ ]*(>|<|=|>=|<=|is[ ]*not|not|is|in)[ ]*([\w]+[.]{0,1}[\w]{0,})$"
        compiler = re.compile(cond_parser_group_reg)
        al = compiler.fullmatch(cond)
        print("condition", cond)
        # if al is None or len(al.groups()) != 3:
        #     raise ValueError(f"Unable to parse {cond1}. Unknown condition")
        dc = DeclareConstraintConditionResolver()
        i = 0
        expression, name_to_cond, cond_to_name = dc.parsed_condition("activation", cond)
        if expression.isliteral:
            print('activation_condition({},T):- {}({},T).\n'.format(i, str(expression), i))
        else:
            conditions = set(name_to_cond.keys())
            l = dc.tree_conditions_to_asp('activation', expression, 'activation_condition', i, conditions)
        # p1 = al.group(1).strip()
        # p2 = al.group(2).strip()
        # p3 = al.group(3).strip()
        # return {"obj_attr": p1, "cond": p2, "val": p3}
        return {"obj_attr": "p1", "cond": "p2", "val": "p3"}

    def __is_reserved_keyboard(self, word: str) -> bool:
        ws = DECLARE_RESERVED.words
        return word in ws


"""
TODO: LP doesn't support float, thus we hav to Scale floating attribute bounds to the lowest integers
"""


class DECLARE2LP:
    #  TODO: Convert declare to .lp  using DeclareModel class.
    lp: LPBUILDER

    def __init__(self) -> None:
        self.lp = LPBUILDER()

    def from_decl(self, model: DeclareModel) -> LPBUILDER:
        keys = model.obj.keys()
        for k in keys:
            obj = model.obj[k]
            obj_typ = obj["object_type"]
            self.lp.define_predicate(k, obj_typ)
            props = obj["props"]
            for attr in props:
                self.lp.define_predicate_attr(k, attr)
                dopt: DeclareObjectPropertyType = props[attr]
                self.lp.set_attr_value(attr, dopt)

        templates_idx = 0
        # for tmp_name in model.templates_dict.keys():
        for ct in model.templates:
            self.lp.add_template(ct.template_name, ct, templates_idx, model.props)
            # template_line.append(f"template({templates_idx},\"{tmp_name}\")")
            templates_idx = templates_idx + 1
        return self.lp


class LPBUILDER:
    lines: typing.List[str] = []
    attributes_values: typing.List[str] = []
    templates_s: typing.List[str] = []

    def define_predicate(self, name: str, predicate_name: str):
        self.lines.append(f'{predicate_name}({name}).')

    def define_predicate_attr(self, name: str, attr: str):
        self.lines.append(f'has_attribute({name}, {attr}).')

    def set_attr_value(self, attr: str, value: DeclareObjectPropertyType):
        val_lp = ""
        if value.is_range_typ:
            v = ""
            if value.typ == DeclarePropertyValueType.FLOAT:
                # TODO: scale to lower int
                pass
            v = value.value.replace("integer", "").replace("between", "").replace("and", "").strip()
            v = ' '.join(v.split())
            v = v.split(" ")
            val_lp = f'value({attr}, {v[0]}..{v[1]}).'
        elif value.typ == DeclarePropertyValueType.ENUMERATION:
            lst = value.value.split(",")
            value_in_lp = []
            for s in lst:
                s = s.strip()
                val_lp_1 = f'value({attr}, {s}).'
                if val_lp_1 not in self.attributes_values:
                    value_in_lp.append(val_lp_1)
                val_lp = "\n".join(value_in_lp)
        else:
            # TODO: check value if it is numeric and float... scale it
            val_lp = f'value({attr}, {value.value}).'

        if val_lp not in self.attributes_values:
            self.attributes_values.append(val_lp)

    def add_template(self, name, ct: ConstraintTemplates, idx: int,
                     props: dict[str, typing.List[DeclareObjectPropertyType]]):
        self.templates_s.append(f"template({idx},\"{name}\").")
        for conds in ct.events_list:  # A, B   <- depends on the type of template
            conds = conds.strip()
            self.templates_s.append(f"activation({idx},{conds}).")
            if ct.active_cond_parsed:
                nameAttr = ct.active_cond_parsed["obj_attr"].split(".")[-1]
                if nameAttr not in props:
                    raise ValueError(f"{nameAttr} not defined in any events")
                dopt = props[nameAttr]
                if dopt[0].typ == DeclarePropertyValueType.ENUMERATION:
                    pass
                print(props[nameAttr])
            print(ct.active_cond_parsed)

            # act_cond = ct.active_cond.strip().replace("")
            # self.templates_s.append(f"activation_condition({idx},T) :- assigned_value({}).")
            self.templates_s.append("\n")
        # ct.

    def parsed_condition(self, condition: typing.Literal['activation', 'correlation'], string: str):
        string = re.sub('\)', ' ) ', string)
        string = re.sub('\(', ' ( ', string)
        string = string.strip()
        string = re.sub(' +', ' ', string)
        string = re.sub('is not', 'is_not', string)
        string = re.sub('not in', 'not_in', string)
        string = re.sub(' *> *', '>', string)
        string = re.sub(' *< *', '<', string)
        string = re.sub(' *= *', '=', string)
        string = re.sub(' *<= *', '<=', string)
        string = re.sub(' *>= *', '>=', string)
        form_list = string.split(" ")

        for i in range(len(form_list) - 1, -1, -1):
            el = form_list[i]
            if el == 'in' or el == 'not_in':
                end_index = form_list[i:].index(')')
                start_index = i - 1
                end_index = end_index + i + 1
                form_list[start_index:end_index] = [' '.join(form_list[start_index:end_index])]
            elif el == 'is' or el == 'is_not':
                start_index = i - 1
                end_index = i + 2
                form_list[start_index:end_index] = [' '.join(form_list[start_index:end_index])]

        for i in range(len(form_list)):
            el = form_list[i]
            if '(' in el and ')' in el:
                el = re.sub('\( ', '(', el)
                el = re.sub(', ', ',', el)
                el = re.sub(' \)', ')', el)
                form_list[i] = el

        keywords = {'and', 'or', '(', ')'}
        c = 0
        name_to_cond = dict()
        cond_to_name = dict()
        for el in form_list:
            if el not in keywords:
                c = c + 1
                name_to_cond[condition + '_condition_' + str(c)] = el
                cond_to_name[el] = condition + '_condition_' + str(c)
        form_string = ''
        for el in form_list:
            if el in cond_to_name:
                form_string = form_string + cond_to_name[el] + ' '
            else:
                form_string = form_string + el + ' '

        algebra = boolean.BooleanAlgebra()
        expression = algebra.parse(form_string, simplify=True)
        return expression, name_to_cond, cond_to_name

    def __str__(self) -> str:
        line = "\n".join(self.lines)
        line = line + "\n\n" + "\n".join(self.attributes_values)
        line = line + "\n\n" + "\n".join(self.templates_s)
        return line
