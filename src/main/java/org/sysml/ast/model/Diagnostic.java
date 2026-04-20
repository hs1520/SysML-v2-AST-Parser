package org.sysml.ast.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Diagnostic {
    private DiagnosticSeverity severity;
    private String message;
    private int line;
    private int column;
    private int length;
    private String source;
}
