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
public class AstNode {
    private String id;
    private String type;
    private String name;
    private String qualifiedName;
    private String visibility;
    private String direction;
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
    @Builder.Default
    private List<String> childIds = new ArrayList<>();
    private String parentId;
    private SourceLocation location;
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceLocation {
        private int startLine;
        private int startColumn;
        private int endLine;
        private int endColumn;
    }
}
