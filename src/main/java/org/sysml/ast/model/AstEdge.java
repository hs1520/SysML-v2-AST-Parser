package org.sysml.ast.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstEdge {
    private String id;
    private String type;
    private String sourceId;
    private String targetId;
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
}
