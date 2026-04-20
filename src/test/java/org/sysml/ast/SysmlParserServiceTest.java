package org.sysml.ast;

import org.junit.jupiter.api.Test;
import org.sysml.ast.model.AstEdge;
import org.sysml.ast.model.AstNode;
import org.sysml.ast.model.DiagnosticSeverity;
import org.sysml.ast.model.ParseResult;
import org.sysml.ast.service.SysmlParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SysmlParserServiceTest {

    @Autowired
    SysmlParserService parserService;

    @Test
    void testParseBasicPackage() {
        String sysml = "package BasicTest { part def Widget { attribute color : String; } }";
        ParseResult result = parserService.parse(sysml, "test.sysml");
        assertTrue(result.isSuccess(), "Parse should succeed");
        assertNotNull(result.getRoot());
        assertFalse(result.getNodes().isEmpty());
    }

    @Test
    void testParseRequirementDef() {
        String sysml = "package Req { requirement def PerfReq { subject v : Vehicle; } }";
        ParseResult result = parserService.parse(sysml, "req.sysml");
        assertTrue(result.isSuccess(), "Parse should succeed");
    }

    @Test
    void testParsePartDef() {
        String sysml = "package Test { part def Engine { attribute power : Real; } }";
        ParseResult result = parserService.parse(sysml, "test.sysml");
        assertTrue(result.isSuccess());
        assertTrue(result.getNodes().values().stream().anyMatch(n -> "PartDef".equals(n.getType())));
    }

    @Test
    void testParseConnector() {
        String sysml = "package Test { part myCar : Vehicle { connect myCar.enginePort to engine.output; } }";
        ParseResult result = parserService.parse(sysml, "test.sysml");
        assertTrue(result.isSuccess());
    }

    @Test
    void testParseSatisfy() {
        String sysml = "package Test { part myCar : Vehicle { satisfy PerfReq; } }";
        ParseResult result = parserService.parse(sysml, "test.sysml");
        assertTrue(result.isSuccess());
        assertTrue(result.getEdges().stream().anyMatch(e -> "SATISFIES".equals(e.getType())));
    }

    @Test
    void testParseError() {
        String sysml = "package { invalid syntax !!! @@@ }";
        ParseResult result = parserService.parse(sysml, "bad.sysml");
        assertFalse(result.isSuccess(), "Parse should fail");
        assertFalse(result.getDiagnostics().isEmpty());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR));
    }

    @Test
    void testParseVehicleFile() throws IOException {
        InputStream is = getClass().getResourceAsStream("/test-samples/vehicle.sysml");
        assertNotNull(is, "vehicle.sysml test resource should exist");
        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        ParseResult result = parserService.parse(content, "vehicle.sysml");
        assertTrue(result.isSuccess(), "Vehicle parse should succeed: " + result.getDiagnostics());
        assertNotNull(result.getRoot());
    }

    @Test
    void testParseDroneExampleFile() throws IOException {
        String content = Files.readString(Path.of("examples/drone.sysml"), StandardCharsets.UTF_8);
        ParseResult result = parserService.parse(content, "drone.sysml");
        assertTrue(result.isSuccess(), "Drone parse should succeed: " + result.getDiagnostics());
        assertNotNull(result.getRoot());
        assertFalse(result.getNodes().isEmpty());
    }

    @Test
    void testRelationshipEdgesReferenceKnownNodeIds() {
        String sysml = """
                package Test {
                    part def Engine specializes BaseEngine { }
                    requirement def PerfReq { }
                    part myCar : Engine {
                        satisfy PerfReq by myCar;
                        refine BaseReq;
                        connect myCar.power to engine.output;
                        import Lib::*;
                    }
                }
                """;

        ParseResult result = parserService.parse(sysml, "relations.sysml");
        assertTrue(result.isSuccess(), "Parse should succeed: " + result.getDiagnostics());

        for (AstEdge edge : result.getEdges()) {
            if ("CONTAINS".equals(edge.getType())) {
                continue;
            }
            assertTrue(result.getNodes().containsKey(edge.getSourceId()), "Missing source node for edge: " + edge);
            assertTrue(result.getNodes().containsKey(edge.getTargetId()), "Missing target node for edge: " + edge);
        }
    }

    @Test
    void testStandaloneSpecializationCreatesGeneralizationNode() {
        String sysml = """
                package Test {
                    part def Vehicle {
                        specializes BaseVehicle;
                    }
                }
                """;

        ParseResult result = parserService.parse(sysml, "generalization.sysml");
        assertTrue(result.isSuccess(), "Parse should succeed: " + result.getDiagnostics());

        AstNode generalization = findNode(result, "Generalization", null);
        assertNotNull(generalization, "Generalization node should be present");

        AstNode vehicle = findNode(result, "PartDef", "Vehicle");
        assertNotNull(vehicle, "PartDef Vehicle should be present");

        boolean hasSpecializes = result.getEdges().stream()
                .anyMatch(e -> "SPECIALIZES".equals(e.getType()) && vehicle.getId().equals(e.getSourceId()));
        assertTrue(hasSpecializes, "PartDef should produce SPECIALIZES edge");
    }

    @Test
    void testSpecializationClauseCapturedOnDefinitionTypes() {
        String sysml = """
                package Test {
                    block Car specializes BaseCar { }
                    requirement def Req specializes BaseReq { }
                    port def EngineIF specializes BaseIF { }
                    attribute def Mass specializes BaseMass { }
                    action def Start specializes BaseStart { }
                }
                """;

        ParseResult result = parserService.parse(sysml, "specializations.sysml");
        assertTrue(result.isSuccess(), "Parse should succeed: " + result.getDiagnostics());

        assertNodeHasSpecialization(result, "Block", "Car", "BaseCar");
        assertNodeHasSpecialization(result, "RequirementDef", "Req", "BaseReq");
        assertNodeHasSpecialization(result, "PortDef", "EngineIF", "BaseIF");
        assertNodeHasSpecialization(result, "AttributeDef", "Mass", "BaseMass");
        assertNodeHasSpecialization(result, "ActionDef", "Start", "BaseStart");
    }

    private AstNode findNode(ParseResult result, String type, String name) {
        return result.getNodes().values().stream()
                .filter(n -> type.equals(n.getType()))
                .filter(n -> name == null || name.equals(n.getName()))
                .findFirst()
                .orElse(null);
    }

    private void assertNodeHasSpecialization(ParseResult result, String type, String name, String target) {
        AstNode node = findNode(result, type, name);
        assertNotNull(node, type + " node should be present: " + name);

        Map<String, Object> properties = node.getProperties();
        assertEquals("specializes", properties.get("specializationKind"));
        Object targets = properties.get("specializations");
        assertTrue(targets instanceof List<?>);
        assertTrue(((List<?>) targets).contains(target));

        boolean hasSpecializesEdge = result.getEdges().stream()
                .anyMatch(e -> "SPECIALIZES".equals(e.getType()) && node.getId().equals(e.getSourceId()));
        assertTrue(hasSpecializesEdge, "Expected SPECIALIZES edge from " + type + " node");
    }
}
