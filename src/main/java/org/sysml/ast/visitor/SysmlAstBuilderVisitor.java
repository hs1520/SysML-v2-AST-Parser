package org.sysml.ast.visitor;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.sysml.ast.model.AstEdge;
import org.sysml.ast.model.AstNode;
import org.sysml.ast.parser.SysMLv2BaseVisitor;
import org.sysml.ast.parser.SysMLv2Parser;

import java.util.*;

public class SysmlAstBuilderVisitor extends SysMLv2BaseVisitor<AstNode> {

    private final Map<String, AstNode> nodes = new LinkedHashMap<>();
    private final List<AstEdge> edges = new ArrayList<>();
    private final Deque<AstNode> parentStack = new ArrayDeque<>();
    private String currentQualifiedPrefix = "";

    public Map<String, AstNode> getNodes() { return nodes; }
    public List<AstEdge> getEdges() { return edges; }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private AstNode.SourceLocation location(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop();
        return AstNode.SourceLocation.builder()
                .startLine(start != null ? start.getLine() : 0)
                .startColumn(start != null ? start.getCharPositionInLine() : 0)
                .endLine(stop != null ? stop.getLine() : 0)
                .endColumn(stop != null ? stop.getCharPositionInLine() : 0)
                .build();
    }

    private AstNode registerNode(AstNode node) {
        nodes.put(node.getId(), node);
        AstNode parent = parentStack.peek();
        if (parent != null) {
            parent.getChildIds().add(node.getId());
            node.setParentId(parent.getId());
            edges.add(AstEdge.builder()
                    .id(newId())
                    .type("CONTAINS")
                    .sourceId(parent.getId())
                    .targetId(node.getId())
                    .build());
        }
        return node;
    }

    private String buildQualifiedName(String name) {
        if (currentQualifiedPrefix.isEmpty()) return name;
        return currentQualifiedPrefix + "::" + name;
    }

    private void withParent(AstNode parent, Runnable r) {
        String prevPrefix = currentQualifiedPrefix;
        if (parent.getName() != null && !parent.getName().isEmpty()) {
            currentQualifiedPrefix = buildQualifiedName(parent.getName());
        }
        parentStack.push(parent);
        r.run();
        parentStack.pop();
        currentQualifiedPrefix = prevPrefix;
    }

    @Override
    public AstNode visitRootNamespace(SysMLv2Parser.RootNamespaceContext ctx) {
        AstNode root = AstNode.builder()
                .id(newId())
                .type("RootNamespace")
                .name("root")
                .qualifiedName("")
                .build();
        registerNode(root);
        withParent(root, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) {
                visit(m);
            }
        });
        return root;
    }

    @Override
    public AstNode visitMember(SysMLv2Parser.MemberContext ctx) {
        String vis = ctx.visibility() != null ? ctx.visibility().getText() : null;
        AstNode node = visit(ctx.memberElement());
        if (node != null && vis != null) {
            node.setVisibility(vis);
        }
        return node;
    }

    @Override
    public AstNode visitMemberElement(SysMLv2Parser.MemberElementContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public AstNode visitPackageDeclaration(SysMLv2Parser.PackageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Package")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitNamespaceDeclaration(SysMLv2Parser.NamespaceDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Namespace")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitPartDefDeclaration(SysMLv2Parser.PartDefDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("PartDef")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.specializationClause() != null) {
            node.getProperties().put("specializes", getQualifiedNames(ctx.specializationClause().qualifiedName()));
        }
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        if (ctx.specializationClause() != null) {
            for (SysMLv2Parser.QualifiedNameContext qn : ctx.specializationClause().qualifiedName()) {
                edges.add(AstEdge.builder()
                        .id(newId()).type("SPECIALIZES")
                        .sourceId(node.getId())
                        .targetId(qn.getText())
                        .build());
            }
        }
        return node;
    }

    @Override
    public AstNode visitBlockDeclaration(SysMLv2Parser.BlockDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Block")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitPartUsageDeclaration(SysMLv2Parser.PartUsageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("PartUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        }
        if (ctx.multiplicityClause() != null) {
            node.getProperties().put("multiplicity", ctx.multiplicityClause().getText());
        }
        registerNode(node);
        if (ctx.member() != null && !ctx.member().isEmpty()) {
            withParent(node, () -> {
                for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
            });
        }
        return node;
    }

    @Override
    public AstNode visitComponentUsageDeclaration(SysMLv2Parser.ComponentUsageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("ComponentUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        }
        registerNode(node);
        if (ctx.member() != null && !ctx.member().isEmpty()) {
            withParent(node, () -> {
                for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
            });
        }
        return node;
    }

    @Override
    public AstNode visitRequirementDefDeclaration(SysMLv2Parser.RequirementDefDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("RequirementDef")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.RequirementBodyContext rb : ctx.requirementBody()) visit(rb);
        });
        return node;
    }

    @Override
    public AstNode visitRequirementUsageDeclaration(SysMLv2Parser.RequirementUsageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("RequirementUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        }
        registerNode(node);
        if (ctx.requirementBody() != null && !ctx.requirementBody().isEmpty()) {
            withParent(node, () -> {
                for (SysMLv2Parser.RequirementBodyContext rb : ctx.requirementBody()) visit(rb);
            });
        }
        return node;
    }

    @Override
    public AstNode visitRequirementBody(SysMLv2Parser.RequirementBodyContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public AstNode visitSubjectClause(SysMLv2Parser.SubjectClauseContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Subject")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitRequireClause(SysMLv2Parser.RequireClauseContext ctx) {
        return visit(ctx.constraintDeclaration());
    }

    @Override
    public AstNode visitPortDefDeclaration(SysMLv2Parser.PortDefDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("PortDef")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitPortUsageDeclaration(SysMLv2Parser.PortUsageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("PortUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.direction() != null) {
            node.setDirection(ctx.direction().getText());
        }
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
            node.getProperties().put("conjugated", ctx.typeClause().TILDE() != null);
        }
        registerNode(node);
        if (ctx.member() != null && !ctx.member().isEmpty()) {
            withParent(node, () -> {
                for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
            });
        }
        return node;
    }

    @Override
    public AstNode visitAttributeDefDeclaration(SysMLv2Parser.AttributeDefDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("AttributeDef")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitAttributeUsageDeclaration(SysMLv2Parser.AttributeUsageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("AttributeUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.direction() != null) {
            node.setDirection(ctx.direction().getText());
        }
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        }
        if (ctx.expression() != null) {
            node.getProperties().put("defaultValue", ctx.expression().getText());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitActionDefDeclaration(SysMLv2Parser.ActionDefDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("ActionDef")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitActionUsageDeclaration(SysMLv2Parser.ActionUsageDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("ActionUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        if (ctx.typeClause() != null) {
            node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        }
        registerNode(node);
        if (ctx.member() != null && !ctx.member().isEmpty()) {
            withParent(node, () -> {
                for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
            });
        }
        return node;
    }

    @Override
    public AstNode visitConnectorDeclaration(SysMLv2Parser.ConnectorDeclarationContext ctx) {
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Connector")
                .name(ctx.name() != null ? ctx.name().getText() : null)
                .location(location(ctx))
                .build();
        List<SysMLv2Parser.FeaturePathContext> fps = ctx.featurePath();
        if (fps.size() >= 2) {
            node.getProperties().put("source", fps.get(0).getText());
            node.getProperties().put("target", fps.get(1).getText());
            edges.add(AstEdge.builder()
                    .id(newId()).type("CONNECTS")
                    .sourceId(fps.get(0).getText())
                    .targetId(fps.get(1).getText())
                    .build());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitSatisfyDeclaration(SysMLv2Parser.SatisfyDeclarationContext ctx) {
        List<SysMLv2Parser.QualifiedNameContext> qns = ctx.qualifiedName();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("SatisfyRelationship")
                .name(qns.isEmpty() ? null : qns.get(0).getText())
                .location(location(ctx))
                .build();
        if (!qns.isEmpty()) {
            node.getProperties().put("requirement", qns.get(0).getText());
        }
        if (qns.size() > 1) {
            node.getProperties().put("satisfiedBy", qns.get(1).getText());
        }
        registerNode(node);
        if (!qns.isEmpty()) {
            AstNode parent = parentStack.peek();
            if (parent != null) {
                edges.add(AstEdge.builder()
                        .id(newId()).type("SATISFIES")
                        .sourceId(parent.getId())
                        .targetId(qns.get(0).getText())
                        .build());
            }
        }
        return node;
    }

    @Override
    public AstNode visitRefineDeclaration(SysMLv2Parser.RefineDeclarationContext ctx) {
        String target = ctx.qualifiedName().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("RefineRelationship")
                .name(target)
                .location(location(ctx))
                .build();
        node.getProperties().put("refines", target);
        registerNode(node);
        AstNode parent = parentStack.peek();
        if (parent != null) {
            edges.add(AstEdge.builder()
                    .id(newId()).type("REFINES")
                    .sourceId(parent.getId())
                    .targetId(target)
                    .build());
        }
        return node;
    }

    @Override
    public AstNode visitConstraintDeclaration(SysMLv2Parser.ConstraintDeclarationContext ctx) {
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Constraint")
                .name(ctx.name() != null ? ctx.name().getText() : null)
                .location(location(ctx))
                .build();
        if (ctx.expression() != null) {
            node.getProperties().put("expression", ctx.expression().getText());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitImportDeclaration(SysMLv2Parser.ImportDeclarationContext ctx) {
        String importPath = ctx.qualifiedNameWithStar().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Import")
                .name(importPath)
                .location(location(ctx))
                .build();
        node.getProperties().put("importedNamespace", importPath);
        if (ctx.visibility() != null) {
            node.setVisibility(ctx.visibility().getText());
        }
        registerNode(node);
        edges.add(AstEdge.builder()
                .id(newId()).type("IMPORTS")
                .sourceId(node.getId())
                .targetId(importPath)
                .build());
        return node;
    }

    @Override
    public AstNode visitDependencyDeclaration(SysMLv2Parser.DependencyDeclarationContext ctx) {
        List<SysMLv2Parser.QualifiedNameContext> qns = ctx.qualifiedName();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Dependency")
                .name(ctx.name() != null ? ctx.name().getText() : null)
                .location(location(ctx))
                .build();
        if (qns.size() >= 2) {
            node.getProperties().put("from", qns.get(0).getText());
            node.getProperties().put("to", qns.get(1).getText());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitCommentDeclaration(SysMLv2Parser.CommentDeclarationContext ctx) {
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Comment")
                .location(location(ctx))
                .build();
        if (ctx.qualifiedName() != null) {
            node.getProperties().put("about", ctx.qualifiedName().getText());
        }
        if (ctx.commentText() != null) {
            node.getProperties().put("text", ctx.commentText().getText());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitDocDeclaration(SysMLv2Parser.DocDeclarationContext ctx) {
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Documentation")
                .location(location(ctx))
                .build();
        node.getProperties().put("text", ctx.BLOCK_COMMENT_TEXT().getText());
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitMetadataDeclaration(SysMLv2Parser.MetadataDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("MetadataAnnotation")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        registerNode(node);
        if (ctx.member() != null && !ctx.member().isEmpty()) {
            withParent(node, () -> {
                for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
            });
        }
        return node;
    }

    @Override
    public AstNode visitSpecializationDeclaration(SysMLv2Parser.SpecializationDeclarationContext ctx) {
        return visitChildren(ctx);
    }

    private List<String> getQualifiedNames(List<SysMLv2Parser.QualifiedNameContext> qns) {
        List<String> names = new ArrayList<>();
        for (SysMLv2Parser.QualifiedNameContext qn : qns) {
            names.add(qn.getText());
        }
        return names;
    }
}
