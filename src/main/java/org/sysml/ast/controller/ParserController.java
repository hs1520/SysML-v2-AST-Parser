package org.sysml.ast.controller;

import org.sysml.ast.model.ParseRequest;
import org.sysml.ast.model.ParseResult;
import org.sysml.ast.service.SysmlParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ParserController {

    @Autowired
    private SysmlParserService parserService;

    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseText(@RequestBody ParseRequest request) {
        if (request == null || isBlank(request.getContent())) {
            return ResponseEntity.badRequest().body(buildBadRequestResult());
        }
        ParseResult result = parserService.parse(request.getContent(), request.getFilename());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parse/file")
    public ResponseEntity<ParseResult> parseFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(buildBadRequestResult());
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (isBlank(content)) {
            return ResponseEntity.badRequest().body(buildBadRequestResult());
        }
        ParseResult result = parserService.parse(content, file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "sysml-ast-parser");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/schema")
    public ResponseEntity<Map<String, Object>> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("version", "1.0");
        schema.put("description", "SysML v2 AST JSON Schema");

        Map<String, Object> nodeTypes = new LinkedHashMap<>();
        nodeTypes.put("Package", "A SysML package declaration");
        nodeTypes.put("Namespace", "A namespace declaration");
        nodeTypes.put("PartDef", "A part definition");
        nodeTypes.put("Block", "A block definition");
        nodeTypes.put("PartUsage", "A part usage");
        nodeTypes.put("RequirementDef", "A requirement definition");
        nodeTypes.put("RequirementUsage", "A requirement usage");
        nodeTypes.put("PortDef", "A port definition");
        nodeTypes.put("PortUsage", "A port usage");
        nodeTypes.put("AttributeDef", "An attribute definition");
        nodeTypes.put("AttributeUsage", "An attribute usage");
        nodeTypes.put("ActionDef", "An action definition");
        nodeTypes.put("ActionUsage", "An action usage");
        nodeTypes.put("ComponentUsage", "A component usage");
        nodeTypes.put("Connector", "A connector");
        nodeTypes.put("SatisfyRelationship", "A satisfy relationship");
        nodeTypes.put("RefineRelationship", "A refine relationship");
        nodeTypes.put("Generalization", "A generalization relationship");
        nodeTypes.put("Constraint", "A constraint");
        nodeTypes.put("Import", "An import declaration");
        nodeTypes.put("Dependency", "A dependency declaration");
        nodeTypes.put("Comment", "A comment");
        nodeTypes.put("Documentation", "A documentation declaration");
        nodeTypes.put("MetadataAnnotation", "A metadata annotation");
        schema.put("nodeTypes", nodeTypes);

        Map<String, Object> edgeTypes = new LinkedHashMap<>();
        edgeTypes.put("CONTAINS", "Parent contains child");
        edgeTypes.put("SPECIALIZES", "Element specializes another");
        edgeTypes.put("SATISFIES", "Part satisfies requirement");
        edgeTypes.put("REFINES", "Element refines another");
        edgeTypes.put("CONNECTS", "Connector connects elements");
        edgeTypes.put("IMPORTS", "Import relationship");
        schema.put("edgeTypes", edgeTypes);

        return ResponseEntity.ok(schema);
    }

    private ParseResult buildBadRequestResult() {
        return ParseResult.builder()
                .success(false)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
