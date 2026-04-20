package org.sysml.ast.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {
    @Builder.Default
    private String version = "1.0";
    private Source source;
    @Builder.Default
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private AstNode root;
    @Builder.Default
    private Map<String, AstNode> nodes = new HashMap<>();
    @Builder.Default
    private List<AstEdge> edges = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private boolean success;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String filename;
        private int lineCount;
        private int characterCount;
    }
}
