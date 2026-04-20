package org.sysml.ast;

import org.junit.jupiter.api.Test;
import org.sysml.ast.model.ParseRequest;
import org.sysml.ast.model.ParseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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

    @Test
    void testParseFileEmptyInput() {
        ByteArrayResource emptyFile = new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "empty.sysml";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", emptyFile);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<ParseResult> response = restTemplate.postForEntity(
                "/api/v1/parse/file",
                requestEntity,
                ParseResult.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testSchemaIncludesDocumentedNodeTypes() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/schema", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map nodeTypes = (Map) response.getBody().get("nodeTypes");
        assertNotNull(nodeTypes);
        assertTrue(nodeTypes.containsKey("ComponentUsage"));
        assertTrue(nodeTypes.containsKey("Dependency"));
        assertTrue(nodeTypes.containsKey("Documentation"));
        assertTrue(nodeTypes.containsKey("MetadataAnnotation"));
        assertTrue(nodeTypes.containsKey("Generalization"));
    }
}
