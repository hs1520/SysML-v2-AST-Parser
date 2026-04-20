# SysML v2 AST Parser

A production-ready Java service that parses SysML v2 text into a structured JSON AST. It provides a REST API and a CLI, and can be deployed with Docker in one step.

## Features

- **Official grammar-based parsing** – uses an ANTLR4 grammar derived from the SysML v2 specification; no regex fallback
- **Structured JSON AST** – stable, versioned schema with nodes, edges, source locations and diagnostics
- **REST API** – `POST /api/v1/parse`, `POST /api/v1/parse/file`, `GET /api/v1/health`, `GET /api/v1/schema`
- **CLI mode** – `java -jar sysml-ast-parser.jar --cli --input=<file>`
- **Docker one-liner** – `docker compose up`
- **Strict error reporting** – parse failures return explicit diagnostics; silent degradation is forbidden

## Quick Start

### Docker (recommended)

```bash
docker compose up
```

The service will be available at `http://localhost:8080`.

### Run locally

```bash
mvn package -DskipTests
java -jar target/sysml-ast-parser-*.jar
```

### CLI mode

```bash
# Parse a file
java -jar target/sysml-ast-parser-*.jar --cli --input=examples/vehicle.sysml

# Parse a file and write JSON to a file
java -jar target/sysml-ast-parser-*.jar --cli --input=examples/vehicle.sysml --output=ast.json
```

## REST API

### Parse SysML text

```
POST /api/v1/parse
Content-Type: application/json

{
  "content": "package Demo { part def Widget { attribute color : String; } }",
  "filename": "demo.sysml"
}
```

### Parse a file upload

```
POST /api/v1/parse/file
Content-Type: multipart/form-data
file=@my-model.sysml
```

### Health check

```
GET /api/v1/health
```

### Schema reference

```
GET /api/v1/schema
```

## JSON AST Schema

See [`docs/api-schema.md`](docs/api-schema.md) for the full schema and field descriptions.

Top-level structure:

```json
{
  "version": "1.0",
  "source": { "filename": "...", "lineCount": 10, "characterCount": 300 },
  "diagnostics": [],
  "success": true,
  "root": { ... },
  "nodes": { "<id>": { ... } },
  "edges": [ { ... } ],
  "metadata": { "timestamp": "...", "antlrVersion": "4.13.1" }
}
```

### Node types

| Type | SysML v2 construct |
|------|--------------------|
| `Package` | `package Foo { }` |
| `Namespace` | `namespace Foo { }` |
| `PartDef` | `part def Vehicle { }` |
| `Block` | `block Car { }` |
| `PartUsage` | `part myCar : Vehicle;` |
| `ComponentUsage` | `component c : C;` |
| `RequirementDef` | `requirement def PerfReq { }` |
| `RequirementUsage` | `requirement perf : PerfReq;` |
| `PortDef` | `port def EngineIF { }` |
| `PortUsage` | `in port enginePort : EngineIF;` |
| `AttributeDef` | `attribute def Mass { }` |
| `AttributeUsage` | `attribute mass : Real = 100.0;` |
| `ActionDef` | `action def Start { }` |
| `ActionUsage` | `action start : Start;` |
| `Connector` | `connect a.p to b.q;` |
| `SatisfyRelationship` | `satisfy PerfReq by myCar;` |
| `RefineRelationship` | `refine BaseReq;` |
| `Constraint` | `constraint { speed > 100 }` |
| `Import` | `import Lib::*;` |
| `Dependency` | `dependency from A to B;` |
| `Comment` | `comment about x { /* ... */ }` |
| `Documentation` | `doc /* ... */` |
| `MetadataAnnotation` | `metadata #Safety { }` |
| `Generalization` | `specializes Base;` |

### Edge types

| Type | Meaning |
|------|---------|
| `CONTAINS` | Parent contains child |
| `SPECIALIZES` | Element specializes another |
| `SATISFIES` | Part satisfies requirement |
| `REFINES` | Element refines another |
| `CONNECTS` | Connector links two elements |
| `IMPORTS` | Import relationship |

## Python Mapping Guide

Map the JSON AST to `SysMLModel` as follows:

```python
import requests, json

resp = requests.post("http://localhost:8080/api/v1/parse",
                     json={"content": sysml_text, "filename": "model.sysml"})
ast = resp.json()

if not ast["success"]:
    raise RuntimeError(ast["diagnostics"])

nodes = ast["nodes"]
edges = ast["edges"]

packages    = [n for n in nodes.values() if n["type"] == "Package"]
blocks      = [n for n in nodes.values() if n["type"] in ("PartDef", "Block")]
requirements= [n for n in nodes.values() if n["type"] == "RequirementDef"]
connectors  = [n for n in nodes.values() if n["type"] == "Connector"]
```

## Building

```bash
mvn package          # build + test
mvn package -DskipTests   # build only
mvn test             # run tests only
```

## Docker build

```bash
docker build -t sysml-ast-parser .
docker run -p 8080:8080 sysml-ast-parser
```

## Project layout

```
src/
├── main/
│   ├── antlr4/org/sysml/ast/parser/SysMLv2.g4   ← ANTLR4 grammar
│   ├── java/org/sysml/ast/
│   │   ├── SysmlAstApplication.java
│   │   ├── cli/CliRunner.java
│   │   ├── controller/ParserController.java
│   │   ├── service/SysmlParserService.java
│   │   ├── visitor/SysmlAstBuilderVisitor.java
│   │   ├── model/{AstNode,AstEdge,ParseResult,...}.java
│   │   └── exception/{ParseException,GlobalExceptionHandler}.java
│   └── resources/application.yml
└── test/
    ├── java/org/sysml/ast/
    │   ├── SysmlParserServiceTest.java
    │   └── ParserControllerTest.java
    └── resources/test-samples/*.sysml
examples/
    └── vehicle.sysml
docs/
    └── api-schema.md
Dockerfile
docker-compose.yml
```
