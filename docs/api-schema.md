# SysML v2 AST Parser API Documentation

## REST API

### POST /api/v1/parse
Parse SysML v2 text and return AST.

**Request:**
```json
{
  "content": "package Test { part def Widget {} }",
  "filename": "test.sysml"
}
```

**Response:** ParseResult JSON

### GET /api/v1/health
Health check endpoint.

### GET /api/v1/schema
Get node/edge type schema.

## AST Node Types
- Package, Namespace, PartDef, Block, PartUsage, RequirementDef, RequirementUsage
- PortDef, PortUsage, AttributeDef, AttributeUsage, ActionDef, ActionUsage
- Connector, SatisfyRelationship, RefineRelationship, Constraint, Import, Comment

## AST Edge Types
- CONTAINS, SPECIALIZES, SATISFIES, REFINES, CONNECTS, IMPORTS
