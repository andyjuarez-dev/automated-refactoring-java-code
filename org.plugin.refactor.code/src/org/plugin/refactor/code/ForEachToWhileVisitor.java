package org.plugin.refactor.code;

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.*;

public class ForEachToWhileVisitor extends ASTVisitor {

	private enum ForEachKind {
	    PRIMITIVE_ARRAY,
	    REFERENCE_ARRAY,
	    ITERABLE,
	    UNKNOWN
	}
	
    private final Set<EnhancedForStatement> markedFors;
    private final ASTRewrite rewrite;
    private final CompilationUnit cu;
    private final Set<String> usedNames = new java.util.HashSet<>();

    private boolean needsIteratorImport = false;
    private boolean needsArraysImport = false;

    public ForEachToWhileVisitor(Set<EnhancedForStatement> markedFors, ASTRewrite rewrite, CompilationUnit cu) {
        this.markedFors = markedFors;
        this.rewrite = rewrite;
        this.cu = cu;
    }

	@Override
	public boolean visit(EnhancedForStatement node) {
    	if (node.getProperty("change") == null)
    		return true;
		
	    if (!markedFors.contains(node)) return true;

	    AST ast = node.getAST();
	    ForEachKind kind = classify(node);

	    /*
	    if (kind == ForEachKind.PRIMITIVE_ARRAY) {
	    	System.out.println("\n---------------- ARRAY PRIMITIVO --------------------------------\n" + node);
	    }
	    else if (kind == ForEachKind.REFERENCE_ARRAY) {
	    	System.out.println("\n---------------- ARRAY DE OBJETOS --------------------------------\n" + node);
	    }
	    else if (kind == ForEachKind.ITERABLE) {
	    	System.out.println("\n---------------- ITERABLE --------------------------------\n" + node);
	    }
	    else 
	    	System.out.println("\n---------------- UNKNOWN --------------------------------\n" + node);
	    */
	    
	    if (kind == ForEachKind.UNKNOWN)
	    	return true;
	    
	    ConversionResult result = switch (kind) {
	        case PRIMITIVE_ARRAY -> convertPrimitiveArray(node, ast);
	        default -> convertWithIterator(node, ast, kind);
	    };

	    insertReplacement(node, result.preStatement, result.whileStmt);
	    return false;
	}

	private ForEachKind classify(EnhancedForStatement node) {
	    ITypeBinding binding = node.getExpression().resolveTypeBinding();
	    if (binding == null)
	    	return ForEachKind.UNKNOWN;
	    
	    if (binding != null && binding.isArray()) {
	        return binding.getElementType().isPrimitive()
	                ? ForEachKind.PRIMITIVE_ARRAY
	                : ForEachKind.REFERENCE_ARRAY;
	    }
	    return ForEachKind.ITERABLE;
	}
	
	private static class ConversionResult {
	    final Statement preStatement;
	    final WhileStatement whileStmt;

	    ConversionResult(Statement pre, WhileStatement w) {
	        this.preStatement = pre;
	        this.whileStmt = w;
	    }
	}

	private ConversionResult convertPrimitiveArray(EnhancedForStatement node, AST ast) {
	    String idxName = uniqueName("idx", node);

	    VariableDeclarationFragment idxFrag = ast.newVariableDeclarationFragment();
	    idxFrag.setName(ast.newSimpleName(idxName));
	    idxFrag.setInitializer(ast.newNumberLiteral("0"));

	    VariableDeclarationStatement idxDecl = ast.newVariableDeclarationStatement(idxFrag);
	    idxDecl.setType(ast.newPrimitiveType(PrimitiveType.INT));

	    InfixExpression cond = ast.newInfixExpression();
	    cond.setOperator(InfixExpression.Operator.LESS);
	    cond.setLeftOperand(ast.newSimpleName(idxName));
	    cond.setRightOperand(arrayLengthExpr(ast, node.getExpression()));

	    WhileStatement whileStmt = ast.newWhileStatement();
	    whileStmt.setExpression(cond);

	    Block body = ast.newBlock();
	    addIndexedElementDeclaration(node, ast, body, idxName);
	    appendBody(ast, body, node.getBody());
	    addIndexIncrement(ast, body, idxName);

	    whileStmt.setBody(body);
	    return new ConversionResult(idxDecl, whileStmt);
	}

	private ConversionResult convertWithIterator(
	        EnhancedForStatement node, AST ast, ForEachKind kind) {

	    needsIteratorImport = true;
	    if (kind == ForEachKind.REFERENCE_ARRAY) {
	        needsArraysImport = true;
	    }

	    String itName = uniqueName("it", node);
	    VariableDeclarationStatement iterDecl =
	            buildIteratorDeclaration(node, ast, itName, kind);

	    WhileStatement whileStmt = ast.newWhileStatement();
	    whileStmt.setExpression(buildHasNext(ast, itName));

	    Block body = ast.newBlock();
	    addIteratorElementDeclaration(node, ast, body, itName);
	    appendBody(ast, body, node.getBody());
	    whileStmt.setBody(body);

	    return new ConversionResult(iterDecl, whileStmt);
	}

	@SuppressWarnings("unchecked")
	private VariableDeclarationStatement buildIteratorDeclaration(
	        EnhancedForStatement node, AST ast, String itName, ForEachKind kind) {

	    VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
	    frag.setName(ast.newSimpleName(itName));
	    frag.setInitializer(buildIteratorInit(node, ast, kind));

	    VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);

	    Type elemType = (Type) ASTNode.copySubtree(ast, node.getParameter().getType());
	    if (elemType.isPrimitiveType()) {
	        elemType = boxedType(ast, (PrimitiveType) elemType);
	    }

	    ParameterizedType iterType =
	            ast.newParameterizedType(ast.newSimpleType(ast.newSimpleName("Iterator")));
	    iterType.typeArguments().add(elemType);
	    decl.setType(iterType);

	    return decl;
	}
	
	@SuppressWarnings("unchecked")
	private Expression buildIteratorInit(
	        EnhancedForStatement node, AST ast, ForEachKind kind) {

	    Expression exprCopy =
	            (Expression) ASTNode.copySubtree(ast, node.getExpression());

	    MethodInvocation iteratorCall;

	    if (kind == ForEachKind.REFERENCE_ARRAY) {
	        MethodInvocation stream = ast.newMethodInvocation();
	        stream.setExpression(ast.newSimpleName("Arrays"));
	        stream.setName(ast.newSimpleName("stream"));
	        stream.arguments().add(exprCopy);

	        iteratorCall = ast.newMethodInvocation();
	        iteratorCall.setExpression(stream);
	        iteratorCall.setName(ast.newSimpleName("iterator"));
	    }
	    else {
	        iteratorCall = ast.newMethodInvocation();
	        iteratorCall.setExpression(exprCopy);
	        iteratorCall.setName(ast.newSimpleName("iterator"));
	    }

	    // ===== Tipo T del foreach =====
	    Type elementType = node.getParameter().getType();

	    // Construimos Iterator<T>
	    ParameterizedType iteratorType = ast.newParameterizedType(
	            ast.newSimpleType(ast.newSimpleName("Iterator"))
	    );

	    iteratorType.typeArguments().add(
	            (Type) ASTNode.copySubtree(ast, elementType)
	    );

	    // Cast a Iterator<T>
	    CastExpression castExpr = ast.newCastExpression();
	    castExpr.setType(iteratorType);
	    castExpr.setExpression(iteratorCall);

	    return castExpr;
	}
	

	@SuppressWarnings("unchecked")
	private void addIteratorElementDeclaration(
	        EnhancedForStatement node,
	        AST ast,
	        Block body,
	        String itName) {

	    // ===== fragment =====
	    VariableDeclarationFragment elemFrag = ast.newVariableDeclarationFragment();

	    // nombre del elemento (ej: e)
	    elemFrag.setName(
	            (SimpleName) ASTNode.copySubtree(ast, node.getParameter().getName())
	    );

	    // ===== it.next() =====
	    MethodInvocation nextCall = ast.newMethodInvocation();
	    nextCall.setExpression(ast.newSimpleName(itName));
	    nextCall.setName(ast.newSimpleName("next"));

	    Expression initializer = nextCall;

	    // ===== Tipo del foreach (T) =====
	    Type elementType = node.getParameter().getType();

	    // Si el tipo NO es simple o puede venir de raw types → agregamos cast
	    // (esto evita errores cuando el iterator no está parametrizado)
	    CastExpression castExpr = ast.newCastExpression();
	    castExpr.setType((Type) ASTNode.copySubtree(ast, elementType));
	    castExpr.setExpression(nextCall);

	    initializer = castExpr;

	    elemFrag.setInitializer(initializer);

	    // ===== Declaración final =====
	    VariableDeclarationStatement elemDecl =
	            ast.newVariableDeclarationStatement(elemFrag);

	    elemDecl.setType(
	            (Type) ASTNode.copySubtree(ast, elementType)
	    );

	    body.statements().add(elemDecl);
	}

	
	@SuppressWarnings("unchecked")
	private void insertReplacement(
	        EnhancedForStatement node,
	        Statement pre,
	        WhileStatement replacement) {

	    AST ast = node.getAST();
	    ASTNode parent = node.getParent();

	    if (parent instanceof Block) {
	        ListRewrite lr =
	                rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
	        lr.insertBefore(pre, node, null);
	        rewrite.replace(node, replacement, null);
	    } else {
	        Block wrapper = ast.newBlock();
	        wrapper.statements().add(pre);
	        wrapper.statements().add(replacement);
	        rewrite.replace(node, wrapper, null);
	    }
	}
	

    private String uniqueName(String base, ASTNode context) {
        // Recolecta nombres ya presentes en el bloque original
        usedNames.addAll(collectNames(context));

        String candidate = base;
        int i = 1;
        while (usedNames.contains(candidate)) {
            candidate = base + i;
            i++;
        }
        usedNames.add(candidate); // <-- ahora registramos el nuevo
        return candidate;
    }

   // @SuppressWarnings("unchecked")
    private Set<String> collectNames(ASTNode context) {
        Set<String> names = new java.util.HashSet<>();
        // Busco el bloque contenedor
        ASTNode block = context;
        while (block != null && !(block instanceof Block)) {
            block = block.getParent();
        }
        if (block instanceof Block b) {
            for (Object o : b.statements()) {
                if (o instanceof VariableDeclarationStatement vds) {
                    for (Object f : vds.fragments()) {
                        names.add(((VariableDeclarationFragment) f).getName().getIdentifier());
                    }
                }
            }
        }
        return names;
    }
    
    
    private Expression arrayLengthExpr(AST ast, Expression arrayExpr) {
        if (arrayExpr instanceof Name name) {
            return ast.newQualifiedName((Name) ASTNode.copySubtree(ast, name), ast.newSimpleName("length"));
        } else {
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression((Expression) ASTNode.copySubtree(ast, arrayExpr));
            fa.setName(ast.newSimpleName("length"));
            return fa;
        }
    }

    private Type boxedType(AST ast, PrimitiveType prim) {
        String wrapper;
        PrimitiveType.Code code = prim.getPrimitiveTypeCode();
        if (code == PrimitiveType.BOOLEAN) wrapper = "Boolean";
        else if (code == PrimitiveType.BYTE) wrapper = "Byte";
        else if (code == PrimitiveType.SHORT) wrapper = "Short";
        else if (code == PrimitiveType.CHAR) wrapper = "Character";
        else if (code == PrimitiveType.INT) wrapper = "Integer";
        else if (code == PrimitiveType.LONG) wrapper = "Long";
        else if (code == PrimitiveType.FLOAT) wrapper = "Float";
        else wrapper = "Double";
        return ast.newSimpleType(ast.newSimpleName(wrapper));
    }
	
    @SuppressWarnings("unchecked")
	private void appendBody(AST ast, Block dest, Statement originalBody) {
        if (originalBody instanceof Block b) {
            for (Object s : b.statements()) {
                dest.statements().add(ASTNode.copySubtree(ast, (ASTNode) s));
            }
        } else {
            dest.statements().add(ASTNode.copySubtree(ast, originalBody));
        }
    }

    private boolean hasImport(String fqn) {
        for (Object o : cu.imports()) {
            ImportDeclaration id = (ImportDeclaration) o;
            if (!id.isOnDemand() && id.getName().getFullyQualifiedName().equals(fqn)) return true;
        }
        return false;
    }
	
    @SuppressWarnings("unchecked")
    private void addIndexedElementDeclaration(
            EnhancedForStatement node,
            AST ast,
            Block body,
            String idxName) {

        // T e = array[idx];
        VariableDeclarationFragment elemFrag = ast.newVariableDeclarationFragment();
        elemFrag.setName(
                (SimpleName) ASTNode.copySubtree(ast, node.getParameter().getName())
        );

        ArrayAccess access = ast.newArrayAccess();
        access.setArray(
                (Expression) ASTNode.copySubtree(ast, node.getExpression())
        );
        access.setIndex(ast.newSimpleName(idxName));

        elemFrag.setInitializer(access);

        VariableDeclarationStatement elemDecl =
                ast.newVariableDeclarationStatement(elemFrag);

        elemDecl.setType(
                (Type) ASTNode.copySubtree(ast, node.getParameter().getType())
        );

        body.statements().add(elemDecl);
    }

    @SuppressWarnings("unchecked")
	private void addIndexIncrement(AST ast, Block body, String idxName) {
        PostfixExpression inc = ast.newPostfixExpression();
        inc.setOperator(PostfixExpression.Operator.INCREMENT);
        inc.setOperand(ast.newSimpleName(idxName));

        body.statements().add(ast.newExpressionStatement(inc));
    }
    
    private Expression buildHasNext(AST ast, String itName) {
        MethodInvocation hasNext = ast.newMethodInvocation();
        hasNext.setExpression(ast.newSimpleName(itName));
        hasNext.setName(ast.newSimpleName("hasNext"));
        return hasNext;
    }

    private void addMissingImports(CompilationUnit cu) {
        AST ast = cu.getAST();
        ListRewrite lr = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);

        if (needsIteratorImport && !hasImport("java.util.Iterator")) {
            ImportDeclaration imp = ast.newImportDeclaration();
            imp.setName(ast.newName("java.util.Iterator"));
            lr.insertLast(imp, null);
        }

        if (needsArraysImport && !hasImport("java.util.Arrays")) {
            ImportDeclaration imp = ast.newImportDeclaration();
            imp.setName(ast.newName("java.util.Arrays"));
            lr.insertLast(imp, null);
        }
    }

    @Override
    public void endVisit(CompilationUnit node) {
        addMissingImports(node);
    }
}
