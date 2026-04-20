# SysML v2 AST Parser – API & Schema Reference

## REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/parse` | Parse SysML v2 text (JSON body) |
| `POST` | `/api/v1/parse/file` | Parse an uploaded `.sysml` file |
| `GET`  | `/api/v1/health` | Liveness check |
| `GET`  | `/api/v1/schema` | Returns node/edge type catalogue |

---

## POST /api/v1/parse

### Request

```json
{
  "content": "package Demo { part def Widget { attribute color : String; } }",
  "filename": "demo.sysml"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `content` | `string` | **yes** | Raw SysML v2 text |
| `filename` | `string` | no | Filename used in diagnostics |

### Response – success

```json
{
  "version": "1.0",
  "success": true,
  "source": {
    "filename": "demo.sysml",
    "lineCount": 1,
    "characterCount": 62
  },
  "diagnostics": [],
  "root": {
    "id": "uuid-root",
    "type": "RootNamespace",
    "name": "root",
    "qualifiedName": "",
    "visibility": null,
    "direction": null,
    "properties": {},
    "childIds": ["uuid-pkg"],
    "parentId": null,
    "location": null,
    "annotations": []
  },
  "nodes": {
    "uuid-root": { "...": "same as root" },
    "uuid-pkg": {
      "id": "uuid-pkg",
      "type": "Package",
      "name": "Demo",
      "qualifiedName": "root::Demo",
      "visibility": null,
      "direction": null,
      "properties": {},
      "childIds": ["uuid-widget"],
      "parentId": "uuid-root",
      "location": {
        "startLine": 1, "startColumn": 0,
        "endLine": 1,   "endColumn": 62
      },
      "annotations": []
    }
  },
  "edges": [
    {
      "id": "uuid-edge",
      "type": "CONTAINS",
      "sourceId": "uuid-root",
      "targetId": "uuid-pkg",
      "properties": {}
    }
  ],
  "metadata": {
    "timestamp": "2024-01-01T00:00:00Z",
    "filename": "demo.sysml",
    "antlrVersion": "4.13.1"
  }
}
```

### Response – parse failure

```json
{
  "version": "1.0",
  "success": false,
  "source": { "filename": "bad.sysml", "lineCount": 1, "characterCount": 10 },
  "diagnostics": [
    {
      "severity": "ERROR",
      "message": "mismatched input '}' expecting {<EOF>, 'package', ...}",
      "line": 1,
      "column": 9,
      "length": 1,
      "source": "SysMLv2Parser"
    }
  ],
  "nodes": {},
  "edges": [],
  "metadata": { "..." : "..." }
}
```

---

## POST /api/v1/parse/file

Multipart form upload:

```
curl -X POST http://localhost:9080/api/v1/parse/file \
     -F "file=@model.sysml"
```

Response format is identical to `/api/v1/parse`.

---

## GET /api/v1/health

```json
{ "status": "UP", "service": "sysml-ast-parser", "version": "1.0.0" }
```

---

## JSON AST Schema

### ParseResult

| Field | Type | Description |
|-------|------|-------------|
| `version` | `string` | Schema version (`"1.0"`) |
| `success` | `boolean` | `true` if parse succeeded with no errors |
| `source` | `Source` | Input metadata |
| `diagnostics` | `Diagnostic[]` | List of errors/warnings/info |
| `root` | `AstNode` | Root namespace node |
| `nodes` | `Map<id, AstNode>` | All AST nodes indexed by UUID |
| `edges` | `AstEdge[]` | All relationships |
| `metadata` | `Map<string, any>` | Parse metadata (timestamp, etc.) |

### Source

| Field | Type | Description |
|-------|------|-------------|
| `filename` | `string` | Filename or `"unknown"` |
| `lineCount` | `integer` | Number of lines |
| `characterCount` | `integer` | Number of characters |

### Diagnostic

| Field | Type | Description |
|-------|------|-------------|
| `severity` | `ERROR\|WARNING\|INFO` | Severity level |
| `message` | `string` | Human-readable message |
| `line` | `integer` | 1-based line number |
| `column` | `integer` | 0-based column position |
| `length` | `integer` | Token length |
| `source` | `string` | Parser component that raised the diagnostic |

### AstNode

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string (UUID)` | Unique node identifier |
| `type` | `string` | Node type (see table below) |
| `name` | `string` | Simple name of the element |
| `qualifiedName` | `string` | Fully qualified name (`::`-separated) |
| `visibility` | `public\|private\|protected\|null` | Access modifier |
| `direction` | `in\|out\|inout\|null` | Feature direction (ports/params) |
| `properties` | `Map<string, any>` | Type-specific properties (e.g. `type`, `value`) |
| `childIds` | `string[]` | IDs of contained children |
| `parentId` | `string\|null` | ID of parent node |
| `location` | `SourceLocation\|null` | Position in source |
| `annotations` | `string[]` | Metadata annotation texts |

### SourceLocation

| Field | Type | Description |
|-------|------|-------------|
| `startLine` | `integer` | 1-based start line |
| `startColumn` | `integer` | 0-based start column |
| `endLine` | `integer` | 1-based end line |
| `endColumn` | `integer` | 0-based end column |

### AstEdge

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string (UUID)` | Unique edge identifier |
| `type` | `string` | Edge type (see table below) |
| `sourceId` | `string` | Source node UUID |
| `targetId` | `string` | Target node UUID |
| `properties` | `Map<string, any>` | Additional edge properties |

---

## Node Types

| Type | SysML v2 construct | Key `properties` |
|------|--------------------|------------------|
| `RootNamespace` | Implicit root | – |
| `Package` | `package Foo { }` | – |
| `Namespace` | `namespace Foo { }` | – |
| `PartDef` | `part def Vehicle { }` | `specializations[]` |
| `Block` | `block Car { }` | `specializations[]` |
| `PartUsage` | `part myCar : Vehicle;` | `type`, `multiplicity` |
| `ComponentUsage` | `component c : C;` | `type` |
| `RequirementDef` | `requirement def PerfReq { }` | `subject` |
| `RequirementUsage` | `requirement perf : PerfReq;` | `type` |
| `PortDef` | `port def EngineIF { }` | `specializations[]` |
| `PortUsage` | `in port enginePort : EngineIF;` | `type`, `direction`, `conjugated` |
| `AttributeDef` | `attribute def Mass { }` | `specializations[]` |
| `AttributeUsage` | `attribute mass : Real = 100.0;` | `type`, `value`, `direction` |
| `ActionDef` | `action def Start { }` | `specializations[]` |
| `ActionUsage` | `action start : Start;` | `type` |
| `Connector` | `connect a.p to b.q;` | `source`, `target` |
| `SatisfyRelationship` | `satisfy PerfReq by myCar;` | `requirement`, `by` |
| `RefineRelationship` | `refine BaseReq;` | `refined` |
| `Generalization` | `specializes Base;` | `general` |
| `Constraint` | `constraint { speed > 100 }` | `expression` |
| `Import` | `import Lib::*;` | `importedName`, `visibility` |
| `Dependency` | `dependency from A to B;` | `from`, `to` |
| `Comment` | `comment about x { /* ... */ }` | `about`, `text` |
| `Documentation` | `doc /* ... */` | `text` |
| `MetadataAnnotation` | `metadata #Safety { }` | `metaClass` |

---

## Edge Types

| Type | Semantics |
|------|-----------|
| `CONTAINS` | Parent namespace/type contains child element |
| `SPECIALIZES` | Element specializes (generalizes) another |
| `SATISFIES` | A part satisfies a requirement |
| `REFINES` | Element refines another requirement/element |
| `CONNECTS` | Connector links a source feature to a target feature |
| `IMPORTS` | Namespace imports another namespace |

---

## Error Codes

The service never falls back to partial parsing. If there are syntax errors, `success` is `false` and `diagnostics` contains all errors.

| Scenario | HTTP status | `success` | `diagnostics` |
|----------|-------------|-----------|---------------|
| Valid SysML, no errors | 200 | `true` | empty array |
| Valid SysML with warnings | 200 | `true` | warnings only |
| Syntax error | 200 | `false` | ERROR entries |
| Empty / missing `content` | 400 | `false` | – |
| Server error | 500 | `false` | error message |

---

## Docker Usage

```bash
# One-line start
docker compose up

# Build image only
docker build -t sysml-ast-parser .

# Run with custom port
docker run -e SERVER_PORT=9090 -p 9090:9090 sysml-ast-parser

# Pass a file for CLI parsing (no server)
docker run --rm -v $(pwd):/data sysml-ast-parser \
  java -jar app.jar --cli --input=/data/model.sysml
```

---

## Python Integration Example

```python
import requests

BASE = "http://localhost:9080/api/v1"

def parse_sysml(text: str, filename: str = "model.sysml") -> dict:
    resp = requests.post(f"{BASE}/parse",
                         json={"content": text, "filename": filename},
                         timeout=30)
    resp.raise_for_status()
    result = resp.json()
    if not result["success"]:
        errors = [d["message"] for d in result["diagnostics"]
                  if d["severity"] == "ERROR"]
        raise ValueError(f"SysML parse errors: {errors}")
    return result

def nodes_by_type(ast: dict, node_type: str) -> list[dict]:
    return [n for n in ast["nodes"].values() if n["type"] == node_type]

# Example
ast = parse_sysml("""
package VehicleDesign {
    part def Vehicle {
        attribute mass : Real;
    }
    requirement def PerformanceReq {
        subject v : Vehicle;
    }
    part myCar : Vehicle {
        satisfy PerformanceReq;
    }
}
""")

packages     = nodes_by_type(ast, "Package")
part_defs    = nodes_by_type(ast, "PartDef")
requirements = nodes_by_type(ast, "RequirementDef")
satisfies    = nodes_by_type(ast, "SatisfyRelationship")

print(f"Packages: {[p['name'] for p in packages]}")
print(f"Part defs: {[p['name'] for p in part_defs]}")
print(f"Requirements: {[r['name'] for r in requirements]}")
```

### Mapping to SysMLModel

```python
class SysMLModel:
    def __init__(self, ast: dict):
        nodes = ast["nodes"]
        edges = ast["edges"]
        
        # Top-level package name
        pkgs = [n for n in nodes.values() if n["type"] == "Package"]
        self.name = pkgs[0]["name"] if pkgs else "unknown"
        
        # Blocks / part defs
        self.blocks = [n for n in nodes.values()
                       if n["type"] in ("PartDef", "Block")]
        
        # Requirements
        self.requirements = [n for n in nodes.values()
                             if n["type"] in ("RequirementDef", "RequirementUsage")]
        
        # Connectors
        self.connectors = [n for n in nodes.values()
                           if n["type"] == "Connector"]
        
        # Retain everything else in metadata
        self.metadata = {
            "source": ast["source"],
            "diagnostics": ast["diagnostics"],
            "all_nodes": nodes,
            "edges": edges,
        }
```

