"""Generate Kotlin serialization models from the shared public OpenAPI schemas."""
import argparse
import json
import re
from pathlib import Path

import yaml

PACKAGE = Path(__file__).resolve().parents[1]
SPEC = PACKAGE / 'contracts/openapi.yaml'
if not SPEC.is_file():
    SPEC = PACKAGE.parents[1] / 'core/templates/core/pages/openapi.yaml'
TARGET = PACKAGE / 'src/main/kotlin/com/classicchess/api/Models.kt'


def pascal(value):
    return ''.join(part[:1].upper() + part[1:] for part in re.split(r'[_-]', value))


class Models:
    def __init__(self, schemas):
        self.schemas = schemas
        self.pending = dict(schemas)
        self.rendered = {}

    def flatten(self, schema):
        if '$ref' in schema:
            return self.flatten(self.schemas[schema['$ref'].rsplit('/', 1)[1]])
        if 'allOf' not in schema:
            return schema
        result = {}
        for member in schema['allOf']:
            member = self.flatten(member)
            for key, value in member.items():
                if key == 'properties':
                    result[key] = {**result.get(key, {}), **value}
                elif key == 'required':
                    result[key] = list(dict.fromkeys(result.get(key, []) + value))
                else:
                    result[key] = value
        return result

    def nullable(self, schema):
        schema = self.flatten(schema)
        kind = schema.get('type', [])
        return kind == 'null' or 'null' in kind or any(
            item.get('type') == 'null' for item in schema.get('anyOf', [])
        )

    def value_type(self, name, schema):
        if '$ref' in schema:
            result = schema['$ref'].rsplit('/', 1)[1]
        else:
            schema = self.flatten(schema)
            variants = schema.get('anyOf', schema.get('oneOf'))
            if variants:
                members = [item for item in variants if item.get('type') != 'null']
                result = self.value_type(name, members[0]) if len(members) == 1 else 'JsonElement'
            else:
                kind = schema.get('type', 'string' if 'const' in schema else None)
                if isinstance(kind, list):
                    kind = next((item for item in kind if item != 'null'), 'null')
                if kind == 'array':
                    result = f'List<{self.value_type(name + "Item", schema.get("items", {}))}>'
                elif kind == 'object' and schema.get('properties'):
                    self.pending.setdefault(name, schema)
                    result = name
                elif kind == 'object' and isinstance(schema.get('additionalProperties'), dict):
                    result = f'Map<String, {self.value_type(name + "Value", schema["additionalProperties"])}>'
                else:
                    result = {'string': 'String', 'integer': 'Long', 'number': 'Double',
                              'boolean': 'Boolean', 'object': 'JsonObject'}.get(kind, 'JsonElement')
        return result.rstrip('?') + '?' if self.nullable(schema) else result

    def render(self):
        while self.pending:
            name, original = next(iter(self.pending.items()))
            del self.pending[name]
            if name in self.rendered:
                continue
            schema = self.flatten(original)
            properties = schema.get('properties', {})
            if not properties:
                self.rendered[name] = f'typealias {name} = {self.value_type(name, schema).rstrip("?")}\n'
                continue
            lines = [f'@Serializable\ndata class {name}(']
            for key, value in properties.items():
                field = pascal(key)
                field = field[:1].lower() + field[1:]
                value_type = self.value_type(name + pascal(key), value)
                optional = key not in schema.get('required', [])
                if optional:
                    value_type = value_type.rstrip('?') + '?'
                annotation = f'@SerialName({json.dumps(key)}) ' if field != key else ''
                default = ' = null' if optional else ''
                lines.append(f'    {annotation}val `{field}`: {value_type}{default},')
            self.rendered[name] = '\n'.join(lines) + '\n)\n'
        return '\n'.join(self.rendered.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    schemas = yaml.safe_load(SPEC.read_text())['components']['schemas']
    output = '// Generated from OpenAPI by scripts/generate_models.py. Do not edit.\n'
    output += 'package com.classicchess.api\n\n'
    output += 'import kotlinx.serialization.SerialName\nimport kotlinx.serialization.Serializable\n'
    output += 'import kotlinx.serialization.json.JsonElement\nimport kotlinx.serialization.json.JsonObject\n\n'
    output += Models(schemas).render()
    if args.check:
        if not TARGET.exists() or TARGET.read_text() != output:
            parser.exit(1, 'Kotlin models differ from OpenAPI; run scripts/generate_models.py.\n')
    else:
        TARGET.write_text(output)


if __name__ == '__main__':
    main()
