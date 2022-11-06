from __future__ import annotations

import warnings
from enum import Enum
import typing
from json import JSONEncoder
import re
import boolean

from log_generator.parsers.declare.declare_model import DeclareEventAttributeType
from log_generator.parsers.declare.declare_model import DeclareEventValueType
from log_generator.parsers.declare.declare_model import DeclareModel
from log_generator.parsers.declare.declare_model import ConstraintTemplates
from log_generator.parsers.declare.declare_constraint_resolver import DeclareConstraintConditionResolver, \
    DeclareConstraintResolver

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


class DeclareParser:
    CONSTRAINTS_TEMPLATES_PATTERN = "^(.*)\[(.*)\]\s*(.*)$"
    declare_content: [str]
    model: DeclareModel
    acts = {}  # { act_name: [] }

    def __init__(self, content: typing.Union[str, typing.List[str]]):
        self.load_declare_content(content)

    def load_declare_content(self, content: typing.Union[str, typing.List[str]]):
        if isinstance(content, str):
            self.declare_content = content.split('\n')
        else:
            self.declare_content = content
        self.model = DeclareModel()

    def parse(self) -> DeclareModel:
        line_index = 0
        for line in self.declare_content:
            line = line.strip('\n').strip()  # clear the string, removing whitespace
            line_index = line_index + 1
            if len(line) > 0 and not line.startswith("#"):
                self.parse_line(line, line_index)
        return self.model

    def parse_line(self, line: str, line_idx: int):
        line = line.strip()
        if self.is_event_name_definition(line):
            self.__parse_event_definition(line, line_idx)
        elif self.is_event_attributes_definition(line):
            self.__parse_attributes_definition(line, line_idx)
        elif self.is_events_attrs_value_definition(line):
            self.__parse_attrs_values_definition(line, line_idx)
        elif self.is_constraint_template_definition(line):
            self.__parse_constraint_template(line, line_idx)
        else:
            raise ValueError(f"Unable to parse the line[{line_idx}]")

    def is_event_name_definition(self, line: str) -> bool:
        x = re.search("^[\w]+ [\w]+$", line, re.MULTILINE)
        return x is not None

    def is_event_attributes_definition(self, line: str) -> bool:
        x = re.search("^bind [a-zA-Z_]+[0-9]* *: *[\w, ]+$", line, re.MULTILINE)
        return x is not None

    def is_events_attrs_value_definition(self, line: str) -> bool:
        x = re.search("^(?!bind)[a-zA-Z_, ]+[0-9]* *: *[\w, ]+", line, re.MULTILINE)
        return x is not None

    def is_constraint_template_definition(self, line: str) -> bool:
        x = re.search(self.CONSTRAINTS_TEMPLATES_PATTERN, line, re.MULTILINE)
        return x is not None

    def __parse_event_definition(self, line: str, line_idx: int, strict=True):
        var_declaration: [str] = line.split(' ')
        if len(var_declaration) != 2:
            raise ValueError(f"Error in line {line_idx}: {line}.\n\tCan have only two words for defining an event: "
                             f"`EventType EventName`")
        event_name = var_declaration[1].strip()
        event_type = var_declaration[0].strip()
        if event_name in self.model.events:
            raise KeyError(f"Error in line {line_idx}: {line}.\n\tMultiple times declaring [{event_name}] event name")
        if strict and self.__is_reserved_keyboard(event_name):
            raise NameError(f"""`{event_name}` is reserved. Cannot be used.""")
        if strict and self.__is_reserved_keyboard(event_type):
            raise NameError(f"""Type of object defined(`{event_type}`) is already reserved.""")
        self.model.events[event_name] = {"object_type": event_type}

    def __parse_attributes_definition(self, line: str, line_idx: int):
        arr = line.replace('bind', '').strip().split(':', maxsplit=1)  # LINE: bind B: grade, mark, name
        event_name = arr[0].strip()  # B
        propsOrAttrs = arr[1].strip().split(',')  # grade, mark, name
        if event_name not in self.model.events:
            raise NameError(f"""Error in line: {line_idx}""")
        obj = self.model.events[event_name]
        if "props" not in obj:
            obj["props"]: typing.Dict[str, DeclareEventAttributeType] = {}
        props = obj["props"]
        for p in propsOrAttrs:
            p = p.strip()
            if self.__is_reserved_keyboard(p):
                raise NameError(f"""Type of object property defined(`{p}`) is already reserved.""")
            props[p] = DeclareEventAttributeType()
            if p in self.model.attributes:
                sp = self.model.attributes[p]
                sp.append(props[p])
            else:
                self.model.attributes[p] = [props[p]]

    def __parse_attrs_values_definition(self, line: str, line_idx: int):
        arr = line.strip().split(':')  # grade, mark: integer between 1 and 5
        if len(arr) != 2:
            raise ValueError(f"Failed to parse in line {line_idx}: {line}")
        props = arr[0].strip().split(",")
        value = arr[1].strip()
        dopt = self.__parse_attr_value(value)
        for p in props:
            p = p.strip()
            if p not in self.model.attributes:
                warnings.warn(f""" "{p}" attribute not defined. Found in line[{line_idx}] "{line}" """)
                continue
            props_of_obj = self.model.attributes[p]
            if props_of_obj:
                for pr in props_of_obj:
                    pr.typ = dopt.typ
                    pr.is_range_typ = dopt.is_range_typ
                    pr.value = dopt.value

    def __parse_attr_value(self, value: str) -> DeclareEventAttributeType:
        # value: integer between 1 and 5     #  <--- integer
        # value: float between 1 and 5       #  <--- float
        # value: x, y, z, v                  #  <--- enumeration
        integer_range_rx = "^integer[ ]+between[ ]+[+-]?\d+[ ]+and[ ]+[+-]?\d$"
        float_range_rx = "^float[ ]+between[ ]+[+-]?\d+(\.\d+)?[ ]+and[ ]+[+-]?\d+(\.\d+)?$"
        enume_rx = "^[\w]+(,[ ]*[\w.]+)*$"  # matches -> [xyz, xyz, dfs], [12,45,78.54,454]
        value = value.strip()
        dopt = DeclareEventAttributeType()
        dopt.value = value
        if re.search(integer_range_rx, value, re.MULTILINE):
            dopt.typ = DeclareEventValueType.INTEGER
            dopt.is_range_typ = True
        elif re.search(float_range_rx, value, re.MULTILINE):  # float
            dopt.typ = DeclareEventValueType.FLOAT
            dopt.is_range_typ = True
        elif re.search(enume_rx, value, re.MULTILINE):  # enumeration
            dopt.typ = DeclareEventValueType.ENUMERATION
            dopt.is_range_typ = False
        else:
            x = re.search("^[+-]?\d+$", value, re.MULTILINE)
            if x:
                dopt.typ = DeclareEventValueType.INTEGER
                dopt.is_range_typ = False
            elif re.search("^[+-]?\d+(\.\d+)?$", value, re.MULTILINE):
                dopt.typ = DeclareEventValueType.FLOAT
                dopt.is_range_typ = False
            else:
                raise ValueError(f"""Unable to parse {value}""")
        return dopt

    def __parse_constraint_template(self, line: str, line_idx: int):
        # Response[A, B] |A.grade = 3 |B.grade > 5 |1,5,s
        d = DeclareConstraintResolver()
        ct = d.resolve(line)
        if ct:
            self.model.templates.append(ct)
            lis = []
            if ct.template_name in self.model.templates_dict:
                lis = self.model.templates_dict
            else:
                self.model.templates_dict[ct.template_name] = lis
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
    lp: LP_BUILDER

    def __init__(self) -> None:
        self.lp = LP_BUILDER()

    def from_decl(self, model: DeclareModel) -> LP_BUILDER:
        keys = model.events.keys()
        for k in keys:
            obj = model.events[k]
            obj_typ = obj["object_type"]
            self.lp.define_predicate(k, obj_typ)
            props = obj["props"]
            for attr in props:
                self.lp.define_predicate_attr(k, attr)
                dopt: DeclareEventAttributeType = props[attr]
                self.lp.set_attr_value(attr, dopt)

        templates_idx = 0
        # for tmp_name in model.templates_dict.keys():
        for ct in model.templates:
            self.lp.add_template(ct.template_name, ct, templates_idx, model.attributes)
            # template_line.append(f"template({templates_idx},\"{tmp_name}\")")
            templates_idx = templates_idx + 1
        return self.lp


class LP_BUILDER:
    lines: typing.List[str] = []
    attributes_values: typing.List[str] = []
    templates_s: typing.List[str] = []

    def define_predicate(self, name: str, predicate_name: str):
        self.lines.append(f'{predicate_name}({name}).')

    def define_predicate_attr(self, name: str, attr: str):
        self.lines.append(f'has_attribute({name}, {attr}).')

    def set_attr_value(self, attr: str, value: DeclareEventAttributeType):
        val_lp = ""
        if value.is_range_typ:
            v = ""
            if value.typ == DeclareEventValueType.FLOAT:
                # TODO: scale to lower int
                pass
            v = value.value.replace("integer", "").replace("between", "").replace("and", "").strip()
            v = ' '.join(v.split())
            v = v.split(" ")
            val_lp = f'value({attr}, {v[0]}..{v[1]}).'
        elif value.typ == DeclareEventValueType.ENUMERATION:
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
                     props: dict[str, typing.List[DeclareEventAttributeType]]):
        self.templates_s.append(f"template({idx},\"{name}\").")
        for conds in ct.events_list:  # A, B   <- depends on the type of template
            conds = conds.strip()
            self.templates_s.append(f"activation({idx},{conds}).")
            if ct.active_cond_parsed:
                nameAttr = ct.active_cond_parsed["obj_attr"].split(".")[-1]
                if nameAttr not in props:
                    raise ValueError(f"{nameAttr} not defined in any events")
                dopt = props[nameAttr]
                if dopt[0].typ == DeclareEventValueType.ENUMERATION:
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
