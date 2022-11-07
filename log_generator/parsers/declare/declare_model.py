from __future__ import annotations

import json
from json import JSONEncoder

from enum import Enum


class ConstraintTemplates:
    template_name: str
    events_list: [str]
    conditions: str
    active_cond: str
    correlation_cond: str
    ts: str

    def __dict__(self):
        return str({
            "template_name": self.template_name, "events_list": self.events_list, "conditions": self.conditions,
            "active_cond": self.active_cond,  "correlation_cond": self.correlation_cond,  "time": self.ts
        })

    def __repr__(self):
        return self.__dict__()

    def __str__(self):
        return f"{{\"name\": {self.template_name}, \"param\": {self.events_list}, \"conditions\": \"{self.conditions}\", " \
               f"\"active_cond\": \"{self.active_cond}\", \"correlation_cond\": \"{self.correlation_cond}\", \"ts\": \"{self.ts}\" }}"


class DeclareEventValueType(str, Enum):
    INTEGER = "integer"
    FLOAT = "float"
    ENUMERATION = "enumeration"


class DeclareEventAttributeType(object):
    typ: DeclareEventValueType = None
    is_range_typ: bool = None
    value: str | [str] = None

    def __init__(self, typ: DeclareEventValueType = None,
                 is_range_typ: bool = None, value: str | Enum | [str] = None):
        self.typ = typ
        self.is_range_typ = is_range_typ
        self.value = value

    # def __iter__(self):
    #     yield from {
    #         "typ": self.typ,
    #         "is_range_typ": self.is_range_typ,
    #         "value": self.value
    #     }.items()

    # def __dict__(self):
    #     return {"typ", self.typ}

    # def __str__(self):
    #     return f"""{{"typ": {self.typ or "null"},"is_range_typ": {self.is_range_typ or "null"},"value": {self.value or "null"}}}"""
    #     # return json.dumps(self, ensure_ascii=False)

    def toJson(self):
        return json.dumps(self, default=lambda o: o.__dict__,)

    def __str__(self):
        return self.toJson().__str__()

    def __repr__(self):
        return self.toJson()
        # return self.__str__()


class DeclareModel(object):
    events: dict[str, dict[str, dict[str, DeclareEventAttributeType] | str]] = {}
    attributes: dict[str, [DeclareEventAttributeType]] = {}
    templates: [ConstraintTemplates] = []
    templates_dict: dict[str, [ConstraintTemplates]] = {}

    def to_str(self) -> str:
        st = f"""{{ "events":{self.events},"templates": {self.templates_dict}, "attributes": {self.attributes} }}"""
        return st.replace("'", '"')

    def __str__(self):
        j = json.loads(self.to_str())
        return json.dumps(j, indent=4)

    def __repr__(self):
        j = json.loads(self.to_str())
        return json.dumps(j, indent=4)

    def __dict__(self):
        return json.loads(self.to_str())

