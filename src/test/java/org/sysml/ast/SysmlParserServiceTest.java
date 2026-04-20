package org.sysml.ast;

import org.junit.jupiter.api.Test;
import org.sysml.ast.model.DiagnosticSeverity;
import org.sysml.ast.model.ParseResult;
import org.sysml.ast.service.SysmlParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
}
