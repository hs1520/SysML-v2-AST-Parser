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
    private final Map<String, String> qualifiedNameToId = new HashMap<>();
    private final Map<String, List<String>> simpleNameToIds = new HashMap<>();
    private final Map<String, String> referenceNodeIds = new HashMap<>();
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
        indexNode(node);
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

    private AstNode registerDetachedNode(AstNode node) {
        indexNode(node);
        return node;
    }

    private void indexNode(AstNode node) {
        nodes.put(node.getId(), node);
        if (node.getQualifiedName() != null && !node.getQualifiedName().isBlank()) {
            qualifiedNameToId.put(node.getQualifiedName(), node.getId());
        }
        if (node.getName() != null && !node.getName().isBlank()) {
            simpleNameToIds.computeIfAbsent(node.getName(), k -> new ArrayList<>()).add(node.getId());
        }
    }

    private String resolveOrCreateReferenceNodeId(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if (qualifiedNameToId.containsKey(reference)) {
            return qualifiedNameToId.get(reference);
        }
        String rootedReference = "root::" + reference;
        if (qualifiedNameToId.containsKey(rootedReference)) {
            return qualifiedNameToId.get(rootedReference);
        }
        if (!reference.contains("::")) {
            List<String> candidates = simpleNameToIds.get(reference);
            if (candidates != null && candidates.size() == 1) {
                return candidates.get(0);
            }
        }
        return referenceNodeIds.computeIfAbsent(reference, key -> {
            String simpleName = key.contains("::") ? key.substring(key.lastIndexOf("::") + 2) : key;
            AstNode refNode = AstNode.builder()
                    .id(newId())
                    .type("Reference")
                    .name(simpleName)
                    .qualifiedName(key)
                    .build();
            refNode.getProperties().put("unresolved", true);
            registerDetachedNode(refNode);
            return refNode.getId();
        });
    }

    private void addRelationshipEdge(String type, String sourceId, String targetQualifiedName) {
        String targetId = resolveOrCreateReferenceNodeId(targetQualifiedName);
        if (sourceId == null || targetId == null) {
            return;
        }
        edges.add(AstEdge.builder()
                .id(newId())
                .type(type)
                .sourceId(sourceId)
                .targetId(targetId)
                .build());
    }

    private String specializationKind(SysMLv2Parser.SpecializationClauseContext clause) {
        if (clause.SPECIALIZES() != null) {
            return "specializes";
        }
        if (clause.SUBSETS() != null) {
            return "subsets";
        }
        return "redefines";
    }

    private void applySpecialization(AstNode node, SysMLv2Parser.SpecializationClauseContext clause) {
        if (clause == null) {
            return;
        }
        List<String> targets = getQualifiedNames(clause.qualifiedName());
        node.getProperties().put("specializations", targets);
        node.getProperties().put("specializationKind", specializationKind(clause));
        for (String target : targets) {
            addRelationshipEdge("SPECIALIZES", node.getId(), target);
        }
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
    public AstNode visitInterfaceDeclaration(SysMLv2Parser.InterfaceDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Interface")
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
    public AstNode visitSystemDefDeclaration(SysMLv2Parser.SystemDefDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("SystemDef")
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
    public AstNode visitPartDefCompactDeclaration(SysMLv2Parser.PartDefCompactDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("PartDef")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        applySpecialization(node, ctx.specializationClause());
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
        return node;
    }

    @Override
    public AstNode visitDataTypeDeclaration(SysMLv2Parser.DataTypeDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("DataType")
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
        applySpecialization(node, ctx.specializationClause());
        registerNode(node);
        withParent(node, () -> {
            for (SysMLv2Parser.MemberContext m : ctx.member()) visit(m);
        });
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
        applySpecialization(node, ctx.specializationClause());
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
        applySpecialization(node, ctx.specializationClause());
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
        applySpecialization(node, ctx.specializationClause());
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
        } else if (ctx.qualifiedName() != null) {
            node.getProperties().put("type", ctx.qualifiedName().getText());
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
    public AstNode visitDirectedFeatureDeclaration(SysMLv2Parser.DirectedFeatureDeclarationContext ctx) {
        String name = ctx.name().getText();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("PortUsage")
                .name(name)
                .qualifiedName(buildQualifiedName(name))
                .location(location(ctx))
                .build();
        node.setDirection(ctx.direction().getText());
        node.getProperties().put("type", ctx.typeClause().qualifiedName().getText());
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitStateBehaviorDeclaration(SysMLv2Parser.StateBehaviorDeclarationContext ctx) {
        AstNode node = AstNode.builder()
                .id(newId())
                .type("StateBehavior")
                .name("behavior")
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> visitChildren(ctx));
        return node;
    }

    @Override
    public AstNode visitStateDeclaration(SysMLv2Parser.StateDeclarationContext ctx) {
        AstNode node = AstNode.builder()
                .id(newId())
                .type("State")
                .name(ctx.name().getText())
                .qualifiedName(buildQualifiedName(ctx.name().getText()))
                .location(location(ctx))
                .build();
        registerNode(node);
        withParent(node, () -> visitChildren(ctx));
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
        applySpecialization(node, ctx.specializationClause());
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
        applySpecialization(node, ctx.specializationClause());
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
            String sourcePath = fps.get(0).getText();
            String targetPath = fps.get(1).getText();
            node.getProperties().put("source", sourcePath);
            node.getProperties().put("target", targetPath);
            String sourceId = resolveOrCreateReferenceNodeId(sourcePath);
            String targetId = resolveOrCreateReferenceNodeId(targetPath);
            edges.add(AstEdge.builder()
                    .id(newId()).type("CONNECTS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build());
        }
        registerNode(node);
        return node;
    }

    @Override
    public AstNode visitSatisfyDeclaration(SysMLv2Parser.SatisfyDeclarationContext ctx) {
        List<SysMLv2Parser.QualifiedNameContext> qns = ctx.qualifiedName();
        String byTarget = ctx.BY() != null && !qns.isEmpty() ? qns.get(qns.size() - 1).getText() : null;
        int requirementEnd = ctx.BY() != null ? qns.size() - 1 : qns.size();
        AstNode node = AstNode.builder()
                .id(newId())
                .type("SatisfyRelationship")
                .name(requirementEnd > 0 ? qns.get(0).getText() : null)
                .location(location(ctx))
                .build();
        List<String> requirements = new ArrayList<>();
        for (int i = 0; i < requirementEnd; i++) {
            requirements.add(qns.get(i).getText());
        }
        if (!requirements.isEmpty()) {
            node.getProperties().put("requirements", requirements);
            node.getProperties().put("requirement", requirements.get(0));
        }
        if (byTarget != null) {
            node.getProperties().put("by", byTarget);
        }
        registerNode(node);
        if (!requirements.isEmpty()) {
            AstNode parent = parentStack.peek();
            String sourceId = parent != null ? parent.getId() : node.getId();
            if (byTarget != null) {
                sourceId = resolveOrCreateReferenceNodeId(byTarget);
            }
            for (String requirement : requirements) {
                addRelationshipEdge("SATISFIES", sourceId, requirement);
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
            addRelationshipEdge("REFINES", parent.getId(), target);
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
        AstNode parent = parentStack.peek();
        String sourceId = parent != null ? parent.getId() : node.getId();
        addRelationshipEdge("IMPORTS", sourceId, importPath);
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
        String text = ctx.BLOCK_COMMENT_TEXT() != null
                ? ctx.BLOCK_COMMENT_TEXT().getText()
                : (ctx.STRING_LITERAL() != null ? ctx.STRING_LITERAL().getText() : "");
        node.getProperties().put("text", text);
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
        AstNode node = AstNode.builder()
                .id(newId())
                .type("Generalization")
                .location(location(ctx))
                .build();
        List<String> targets = getQualifiedNames(ctx.specializationClause().qualifiedName());
        node.getProperties().put("general", targets);
        node.getProperties().put("specializationKind", specializationKind(ctx.specializationClause()));
        registerNode(node);

        AstNode parent = parentStack.peek();
        if (parent != null) {
            for (String target : targets) {
                addRelationshipEdge("SPECIALIZES", parent.getId(), target);
            }
        }
        return node;
    }

    private List<String> getQualifiedNames(List<SysMLv2Parser.QualifiedNameContext> qns) {
        List<String> names = new ArrayList<>();
        for (SysMLv2Parser.QualifiedNameContext qn : qns) {
            names.add(qn.getText());
        }
        return names;
    }
}
