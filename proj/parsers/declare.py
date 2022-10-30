from enum import Enum
import typing
import json
import re

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
 


"""


class DECLARE_RESERVED:
    words = ["is", "not", "in", "between", "integer", "float", "enumeration", "and"]
    condition_symbols = [">", "<", "=", "is", ">=", "<="]


class DECLARE_LINE(Enum):
    DEFINE_EVENT_LINE = 1
    DEFINE_PROP_LINE = 2  # TODO: fix name
    DEFINE_PROP_VALUE_LINE = 3  # Constraints
    DEFINE_CONSTRAINT_TEMPLATE = 4


class DECLARE2LP:
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
    acts = {}  # { act_name: [] }

    def __init__(self, content: typing.Union[str, typing.List[str]]):
        self.load_declare_content(content)

    def load_declare_content(self, content: typing.Union[str, typing.List[str]]):
        if isinstance(content, str):
            self.declare_content = content.split('\n')
        else:
            self.declare_content = content

    def parse(self):
        for line in self.declare_content:
            line = line.strip('\n').strip()  # clear the string, removing whitespace
            if len(line) > 0 and line.startswith("#"):
                # x = self.is_object_defination(line)
                # print(f'Is line object defining({x}): ', line)
                x = self.is_object_prop_definition(line)
                print(f'Is line prop defining({x}): ', line)
            # self.__parse_act_definition(line)
        # print(json.dumps(self.acts, indent=4))
        # print(self.acts)

    def detect_line(self, line: str):
        line = line.strip()
        if self.is_object_definition(line):
            self.__parse_act_definition(line)

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

    def __parse_act_definition(self, line: str):
        line = line.strip()
        p = line.split(' ')
        act_list = []
        if p[0] in self.acts:
            act_list = self.acts[p[0].strip()]  # activity
            act_list.append(p[1:])
        else:
            act_list = [p[1:]]
            self.acts[p[0]] = act_list
