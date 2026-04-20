package org.sysml.ast.service;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.sysml.ast.model.*;
import org.sysml.ast.parser.SysMLv2Lexer;
import org.sysml.ast.parser.SysMLv2Parser;
import org.sysml.ast.visitor.SysmlAstBuilderVisitor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class SysmlParserService {

    public ParseResult parse(String content, String filename) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        CharStream input = CharStreams.fromString(content);
        SysMLv2Lexer lexer = new SysMLv2Lexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new DiagnosticErrorListener(diagnostics, DiagnosticSeverity.ERROR));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SysMLv2Parser parser = new SysMLv2Parser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new DiagnosticErrorListener(diagnostics, DiagnosticSeverity.ERROR));

        ParseTree tree = parser.rootNamespace();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("filename", filename != null ? filename : "unknown");
        metadata.put("antlrVersion", "4.13.1");

        ParseResult.Source source = ParseResult.Source.builder()
                .filename(filename != null ? filename : "unknown")
                .lineCount(content.split("\n", -1).length)
                .characterCount(content.length())
                .build();

        boolean hasErrors = diagnostics.stream()
                .anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR);

        if (hasErrors) {
            return ParseResult.builder()
                    .version("1.0")
                    .success(false)
                    .source(source)
                    .diagnostics(diagnostics)
                    .nodes(new HashMap<>())
                    .edges(new ArrayList<>())
                    .metadata(metadata)
                    .build();
        }

        SysmlAstBuilderVisitor visitor = new SysmlAstBuilderVisitor();
        AstNode root = visitor.visit(tree);

        return ParseResult.builder()
                .version("1.0")
                .success(true)
                .source(source)
                .diagnostics(diagnostics)
                .root(root)
                .nodes(visitor.getNodes())
                .edges(visitor.getEdges())
                .metadata(metadata)
                .build();
    }

    private static class DiagnosticErrorListener extends BaseErrorListener {
        private final List<Diagnostic> diagnostics;
        private final DiagnosticSeverity severity;

        DiagnosticErrorListener(List<Diagnostic> diagnostics, DiagnosticSeverity severity) {
            this.diagnostics = diagnostics;
            this.severity = severity;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e) {
            diagnostics.add(Diagnostic.builder()
                    .severity(severity)
                    .message(msg)
                    .line(line)
                    .column(charPositionInLine)
                    .length(1)
                    .source("SysMLv2Parser")
                    .build());
        }
    }
}
