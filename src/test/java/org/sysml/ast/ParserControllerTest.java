package org.sysml.ast;

import org.junit.jupiter.api.Test;
import org.sysml.ast.model.ParseRequest;
import org.sysml.ast.model.ParseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParserControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void testHealth() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/health", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void testParseEndpoint() {
        ParseRequest request = new ParseRequest();
        request.setContent("package Test { part def Widget { attribute color : String; } }");
        request.setFilename("test.sysml");
        ResponseEntity<ParseResult> response = restTemplate.postForEntity("/api/v1/parse", request, ParseResult.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void testParseInvalidInput() {
        ParseRequest request = new ParseRequest();
        request.setContent("package { @@@ invalid }");
        ResponseEntity<ParseResult> response = restTemplate.postForEntity("/api/v1/parse", request, ParseResult.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }
}
