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

/**
 * AST visitor responsible for transforming marked
 * {@link EnhancedForStatement} nodes into semantically
 * equivalent {@link WhileStatement} structures.
 *
 * <p>The transformation distinguishes between different iteration kinds:</p>
 * <ul>
 *   <li>Primitive arrays</li>
 *   <li>Reference arrays</li>
 *   <li>Iterable implementations</li>
 * </ul>
 *
 * <p>Depending on the detected type, the visitor generates either:</p>
 * <ul>
 *   <li>An index-based while loop (for primitive arrays)</li>
 *   <li>An {@code Iterator<T>}-based loop (for reference arrays and Iterables)</li>
 * </ul>
 *
 * <p>The transformation preserves execution semantics by:</p>
 * <ul>
 *   <li>Maintaining evaluation order</li>
 *   <li>Ensuring correct element typing (including boxing when required)</li>
 *   <li>Introducing casts where necessary</li>
 *   <li>Preventing variable name collisions</li>
 *   <li>Adding required imports if missing</li>
 * </ul>
 *
 * <p>Only {@code enhanced for} statements annotated with the
 * property {@code "change"} are transformed.</p>
 * 
 */
public class ForEachToWhileVisitor extends ASTVisitor {

	/**
     * Classification of the enhanced for iteration source. 
	 */
	private enum ForEachKind {
	    PRIMITIVE_ARRAY,
	    REFERENCE_ARRAY,
	    ITERABLE,
	    UNKNOWN
	}
	
	/**
     * Set of {@link EnhancedForStatement} nodes identified
     * as requiring transformation.
	 */
    private final Set<EnhancedForStatement> markedFors;

    /** Rewriter used to apply structural modifications to the AST.	*/
    private final ASTRewrite rewrite;

    /** The compilation unit of the source file, used to inspect existing imports. */
    private final CompilationUnit cu;

    /** Names generated during the transformation process to ensure uniqueness.	 */
    private final Set<String> usedNames = new java.util.HashSet<>();

    /** Flag indicating if 'java.util.Iterator' needs to be added to the imports. */
    private boolean needsIteratorImport = false;
    
    /** Flag indicating if 'java.util.Arrays' needs to be added to the imports. */
    private boolean needsArraysImport = false;

    /**
     * Creates a new visitor that converts marked enhanced-for
     * statements using the provided rewrite context.
     * 
     * @param markedFors set of loops previously marked for conversion
     * @param rewrite AST rewriter used to apply structural changes
     * @param cu compilation unit used for import management
     */
    public ForEachToWhileVisitor(Set<EnhancedForStatement> markedFors, ASTRewrite rewrite, CompilationUnit cu) {
        this.markedFors = markedFors;
        this.rewrite = rewrite;
        this.cu = cu;
    }

    /**
     * Visits {@code EnhancedForStatement} (for-each) nodes to perform their conversion 
     * into {@code WhileStatement} structures.
     * The process follows these steps:
     * <ol>
     * <li>Validation: Checks if the node is flagged for transformation.</li>
     * <li>Classification: Determines if the expression is a primitive array, 
     * an object array, or a {@code Iterable} collection.</li>
     * <li>Conversion: Dispatches the transformation to specialized methods 
     * based on the detected type.</li>
     * <li>Replacement: Updates the AST with the generated while-loop and 
     * any required setup statements (e.g., iterator initialization).</li>
     * </ol>
     *
     * @param node The {@code EnhancedForStatement} node to be transformed.
     * @return false to prevent visiting nested nodes that are already being refactored, 
     * true if no transformation is performed.
     * 
     */
	@Override
	public boolean visit(EnhancedForStatement node) {
        // Skip nodes not marked for transformation
    	if (node.getProperty("change") == null)
    		return true;
		
	    if (!markedFors.contains(node)) return true;

	    AST ast = node.getAST();
	    
        /* 
         * Identify the type of the expression being iterated (Array vs Collection).
         * This classification determines the specific while-loop template to use.
         */
	    ForEachKind kind = classify(node);
	    
	    if (kind == ForEachKind.UNKNOWN)
	    	return true;
	    
	    ConversionResult result = switch (kind) {
	        case PRIMITIVE_ARRAY -> convertPrimitiveArray(node, ast);
	        default -> convertWithIterator(node, ast, kind);
	    };

        /* 
         * Finalize the transformation by inserting the replacement block 
         * into the ASTRewrite stream.
         */
	    insertReplacement(node, result.preStatement, result.whileStmt);
	    return false;
	}

	/**
     * Determines the iteration kind of the enhanced-for source expression.	 
     *  
	 * @param node the enhanced for statement
	 * @return the detected {@link ForEachKind}
	 */
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

	/**
     * A helper class to store the result of a for-each to while conversion.
     * Since the transformation requires both a setup statement (such as an 
     * iterator or index initialization) and the actual loop, this class 
     * wraps both components to be inserted into the AST together.
	 * 
	 */
	private static class ConversionResult {
        /** The initialization statement to be placed before the loop. */
		final Statement preStatement;

        /** The generated while loop. */
		final WhileStatement whileStmt;

        /**
         * @param pre The setup statement (initialization).
         * @param w The constructed while statement.
         */
	    ConversionResult(Statement pre, WhileStatement w) {
	        this.preStatement = pre;
	        this.whileStmt = w;
	    }
	}

	/**
     * Converts an enhanced-for over a primitive array into
     * an index-based while loop.
     *
     * <p>Generates:</p>
     * <pre>
     * int idx = 0;
     * while (idx &lt; array.length) {
     *     T element = array[idx];
     *     ...
     *     idx++;
     * }
     * </pre>
	 * 
	 * @param node the enhanced for statement
	 * @param ast the current AST instance
	 * @return an object ConversionResult (index-based)
	 */
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

	/**
     * Converts an enhanced-for over an Iterable or reference array
     * into an iterator-based while loop.
     *
     * <p>Generates:</p>
     * <pre>
     * Iterator&lt;T&gt; it = ...;
     * while (it.hasNext()) {
     *     T element = it.next();
     *     ...
     * }
     * </pre>
	 * 
	 * @param node the enhanced for statement
	 * @param ast the current AST instance
	 * @param kind the kind of foreach
	 * @return an object ConversionResult (iterator-based)
	 */
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

	
	/**
     * Constructs the iterator declaration statement required for the while-loop.
     * This method creates a statement like {@code Iterator<Type> it = collection.iterator();}.
     * It automatically handles type boxing if the for-each parameter is a primitive type,
     * ensuring the {@code Iterator} is correctly parameterized.
	 * 
	 * @param node The original for-each statement.
	 * @param ast The AST instance to create new nodes.
	 * @param itName The unique name generated for the new iterator variable.
	 * @param kind The classification of the for-each (Collection vs Array).
	 * @return A {@code VariableDeclarationStatement} for the iterator.
	 */
	@SuppressWarnings("unchecked")
	private VariableDeclarationStatement buildIteratorDeclaration(
			EnhancedForStatement node, AST ast, String itName, ForEachKind kind) {

	    // 1. Create the fragment (it = collection.iterator())
	    VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
	    frag.setName(ast.newSimpleName(itName));
	    frag.setInitializer(buildIteratorInit(node, ast, kind));

	    VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);

	    // 2. Determine the element type and apply boxing if necessary
	    Type elemType = (Type) ASTNode.copySubtree(ast, node.getParameter().getType());
	    if (elemType.isPrimitiveType()) {
	        elemType = boxedType(ast, (PrimitiveType) elemType);
	    }

	    // 3. Create the ParameterizedType: Iterator<ElemType>
	    ParameterizedType iterType =
	            ast.newParameterizedType(ast.newSimpleType(ast.newSimpleName("Iterator")));
	    iterType.typeArguments().add(elemType);
	    decl.setType(iterType);

	    return decl;
	}


	/**
     * Builds the initialization expression for the iterator.
     * Depending on the expression type, it generates:
     * <ul>
     * <li>For Collections: {@code (Iterator<Type>) collection.iterator()}</li>
     * <li>For Reference Arrays: {@code (Iterator<Type>) Arrays.stream(array).iterator()}</li>
     * </ul>
	 * 
	 * @param node The original for-each statement.
	 * @param ast The AST instance.
	 * @param kind The detected kind of the for-each expression (Array vs. Collection).
	 * @return A {@code CastExpression} containing the iterator initialization logic.
	 */
	@SuppressWarnings("unchecked")
	private Expression buildIteratorInit(
	        EnhancedForStatement node, AST ast, ForEachKind kind) {

	    Expression exprCopy =
	            (Expression) ASTNode.copySubtree(ast, node.getExpression());

	    MethodInvocation iteratorCall;

	    /* 
	     * If it's a reference array, we use Java Streams to obtain an iterator.
	     * This allows us to treat arrays and collections uniformly.
	     */
	    if (kind == ForEachKind.REFERENCE_ARRAY) {
	        // Generates: Arrays.stream(expr)
	    	MethodInvocation stream = ast.newMethodInvocation();
	        stream.setExpression(ast.newSimpleName("Arrays"));
	        stream.setName(ast.newSimpleName("stream"));
	        stream.arguments().add(exprCopy);

	        // Generates: Arrays.stream(expr).iterator()
	        iteratorCall = ast.newMethodInvocation();
	        iteratorCall.setExpression(stream);
	        iteratorCall.setName(ast.newSimpleName("iterator"));
	    }
	    else {
	        iteratorCall = ast.newMethodInvocation();
	        iteratorCall.setExpression(exprCopy);
	        iteratorCall.setName(ast.newSimpleName("iterator"));
	    }

	    // Set up the parameterized type for the Cast: (Iterator<ElementType>)
	    Type elementType = node.getParameter().getType();

	    ParameterizedType iteratorType = ast.newParameterizedType(
	            ast.newSimpleType(ast.newSimpleName("Iterator"))
	    );

	    iteratorType.typeArguments().add(
	            (Type) ASTNode.copySubtree(ast, elementType)
	    );

	    // Wrap everything in a CastExpression to ensure type safety in the generated code
	    CastExpression castExpr = ast.newCastExpression();
	    castExpr.setType(iteratorType);
	    castExpr.setExpression(iteratorCall);

	    return castExpr;
	}
	


	/**
     * Declares the loop element variable at the beginning of the while-body.
     * This method generates a statement such as {@code Type element = (Type) it.next();}.
     * It ensures that the rest of the loop body can reference the original element 
     * name without further modifications.
	 * 
	 * @param node The original for-each statement.
	 * @param ast The AST instance.
	 * @param body The block representing the new while-loop body.
	 * @param itName The name of the iterator variable previously declared.
	 */
	@SuppressWarnings("unchecked")
	private void addIteratorElementDeclaration(
	        EnhancedForStatement node,
	        AST ast,
	        Block body,
	        String itName) {

	    // 1. Create the fragment with the original variable name (e.g., 'it1')
	    VariableDeclarationFragment elemFrag = ast.newVariableDeclarationFragment();

	    elemFrag.setName(
	            (SimpleName) ASTNode.copySubtree(ast, node.getParameter().getName())
	    );

	    // 2. Prepare the 'it.next()' call
	    MethodInvocation nextCall = ast.newMethodInvocation();
	    nextCall.setExpression(ast.newSimpleName(itName));
	    nextCall.setName(ast.newSimpleName("next"));

	    Expression initializer = nextCall;

	    // 3. Apply a Cast to the element type: (Type) it.next()
	    // This is crucial for compatibility with generic Iterators in some contexts.
	    Type elementType = node.getParameter().getType();

	    CastExpression castExpr = ast.newCastExpression();
	    castExpr.setType((Type) ASTNode.copySubtree(ast, elementType));
	    castExpr.setExpression(nextCall);

	    initializer = castExpr;

	    elemFrag.setInitializer(initializer);

	    // 4. Create the full declaration statement: Type item = (Type) it.next();
	    VariableDeclarationStatement elemDecl = ast.newVariableDeclarationStatement(elemFrag);

	    elemDecl.setType(
	            (Type) ASTNode.copySubtree(ast, elementType)
	    );

	    // Insert the declaration at the very beginning of the new while body
	    body.statements().add(elemDecl);
	}


	/**
     * Integrates the transformed while-loop and its initialization statement into the AST.
     * This method handles two structural scenarios:
     * <ol>
     * <li>If the parent is a {@code Block}, the initialization is inserted 
     * immediately before the original loop.</li>
     * <li>If the parent is not a block (e.g., a single-statement {@code if}), 
     * both the initialization and the while-loop are wrapped in a new 
     * {@code Block} to preserve syntax validity.</li>
     * </ol>
	 * 
	 * @param node The original {@code EnhancedForStatement} node.
	 * @param pre The initialization statement (e.g., iterator declaration).
	 * @param replacement The newly constructed {@code WhileStatement}.
	 */
	@SuppressWarnings("unchecked")
	private void insertReplacement(
	        EnhancedForStatement node,
	        Statement pre,
	        WhileStatement replacement) {

	    AST ast = node.getAST();
	    ASTNode parent = node.getParent();

	    /* 
	     * Scenario 1: The loop is inside a standard block { ... }
	     * We use ListRewrite to inject the 'pre' statement before the loop.
	     */
	    if (parent instanceof Block) {
	        ListRewrite lr =
	                rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
	        lr.insertBefore(pre, node, null);
	        rewrite.replace(node, replacement, null);
	    }
	    /* 
	     * Scenario 2: The loop is a standalone statement (e.g., if (cond) for(...))
	     * We must wrap both 'pre' and 'while' in a new Block to avoid syntax errors.
	     */
	    else {
	        Block wrapper = ast.newBlock();
	        wrapper.statements().add(pre);
	        wrapper.statements().add(replacement);
	        rewrite.replace(node, wrapper, null);
	    }
	}
	

	/**
     * Generates a unique variable name within the given context to avoid naming collisions.
     * <p>
     * It first collects all existing names in the current scope and then appends a 
     * numeric suffix to the base name if a conflict is detected (e.g., "it" becomes "it1").
     * </p>
	 * 
	 * @param base The preferred base name for the new variable.
	 * @param context The AST node used as a reference to determine the current scope.
	 * @return A unique string identifier.
	 */
    private String uniqueName(String base, ASTNode context) {
        // Refresh the set of unavailable names based on the current context
    	usedNames.addAll(collectNames(context));

        String candidate = base;
        int i = 1;

        // Increment suffix until a name not present in usedNames is found
        while (usedNames.contains(candidate)) {
            candidate = base + i;
            i++;
        }
        // Reserve the name so it's not picked again in the same transformation pass
        usedNames.add(candidate); 
        return candidate;
    }

    /**
     * Scans the surrounding block of a given node to collect all declared variable names.
     * <p>
     * This method traverses up the AST to find the nearest enclosing {@code Block} 
     * and extracts identifiers from all local {@code VariableDeclarationStatement} nodes.
     * </p>
     * 
     * @param context The node from which to start searching for the scope.
     * @return A set of variable names already in use within the identified block.
     */
   // @SuppressWarnings("unchecked")
    private Set<String> collectNames(ASTNode context) {
        Set<String> names = new java.util.HashSet<>();
        ASTNode block = context;
        // Traverse up the tree to find the parent Block (the scope boundary)
        while (block != null && !(block instanceof Block)) {
            block = block.getParent();
        }
        // Extract names from all variable declarations in the block
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
    
    /**
     * Creates an expression to access the length of an array.
     * <p>
     * It intelligently chooses between a {@code QualifiedName} (e.g., {@code myVar.length}) 
     * and a {@code FieldAccess} (e.g., {@code getArray().length}) based on the 
     * structure of the input expression to ensure valid AST generation.
     * </p>
     * 
     * @param ast The AST instance.
     * @param arrayExpr The expression representing the array.
     * @return An expression representing the '.length' access.
     */
    private Expression arrayLengthExpr(AST ast, Expression arrayExpr) {
        if (arrayExpr instanceof Name name) {
            // Case for simple or qualified names: arrayName.length
            return ast.newQualifiedName((Name) ASTNode.copySubtree(ast, name), ast.newSimpleName("length"));
        } else {
            // Case for more complex expressions: (someCall()).length
            FieldAccess fa = ast.newFieldAccess();
            fa.setExpression((Expression) ASTNode.copySubtree(ast, arrayExpr));
            fa.setName(ast.newSimpleName("length"));
            return fa;
        }
    }

    /**
     * Maps a primitive type to its corresponding object wrapper type (Autoboxing).
     * <p>
     * This is required when converting for-each loops over primitive arrays into 
     * iterator-based loops, as {@code Iterator} only supports object types.
     * </p>
     * 
     * @param ast The AST instance.
     * @param prim The primitive type to be boxed.
     * @return A {@code SimpleType} representing the wrapper class (e.g., Integer, Boolean).
     */
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

    /**
     * Transfers the statements from the original for-each body to the new while-body.
     * <p>
     * It prevents unnecessary block nesting by unwrapping the original block 
     * if it exists, ensuring the resulting code remains clean and readable.
     * </p>
     * 
     * @param ast The AST instance.
     * @param dest The destination block (the new while-body).
     * @param originalBody The body of the original enhanced-for statement.
     */
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

    /**
     * Checks if a specific library or class is already present in the compilation unit's imports.
     * <p>
     * This is used to determine if utility classes like {@code java.util.Iterator} 
     * or {@code java.util.Arrays} need to be added to the source file.
     * </p>
     * 
     * @param fqn The Fully Qualified Name (FQN) of the class to check.
     * @return {@code true} if the import is already explicitly declared; {@code false} otherwise.
     */
    private boolean hasImport(String fqn) {
        for (Object o : cu.imports()) {
            ImportDeclaration id = (ImportDeclaration) o;
            // Matches explicit imports (e.g., java.util.Iterator) excluding on-demand ones (*)
            if (!id.isOnDemand() && id.getName().getFullyQualifiedName().equals(fqn)) return true;
        }
        return false;
    }

    /**
     * Declares the loop element variable by accessing the array at the current index.
     * <p>
     * It generates a statement like {@code Type element = array[index];} at the 
     * beginning of the while-body, allowing the original loop logic to remain 
     * unchanged.
     * </p>
     * 
     * @param node The original for-each statement.
     * @param ast The AST instance.
     * @param body The new while-loop body.
     * @param idxName The name of the index variable (e.g., "i").
     */
    @SuppressWarnings("unchecked")
    private void addIndexedElementDeclaration(
            EnhancedForStatement node,
            AST ast,
            Block body,
            String idxName) {

        // T e = array[idx];
        // 1. Create the fragment: element = array[idx]
        VariableDeclarationFragment elemFrag = ast.newVariableDeclarationFragment();
        elemFrag.setName(
                (SimpleName) ASTNode.copySubtree(ast, node.getParameter().getName())
        );

        // 2. Set up the array access expression: array[index]
        ArrayAccess access = ast.newArrayAccess();
        access.setArray(
                (Expression) ASTNode.copySubtree(ast, node.getExpression())
        );
        access.setIndex(ast.newSimpleName(idxName));

        elemFrag.setInitializer(access);

        // 3. Create the full declaration: Type element = array[idx];
        VariableDeclarationStatement elemDecl =
                ast.newVariableDeclarationStatement(elemFrag);

        elemDecl.setType(
                (Type) ASTNode.copySubtree(ast, node.getParameter().getType())
        );

        // Add to the top of the body so it's available for the rest of the statements
        body.statements().add(elemDecl);
    }

    /**
     * Appends the index increment statement to the end of the loop body.
     * <p>
     * It generates {@code index++;} to ensure the loop progresses through the 
     * array elements.
     * </p>
     * 
     * @param ast The AST instance.
     * @param body The while-loop body.
     * @param idxName The name of the index variable to increment.
     */
    @SuppressWarnings("unchecked")
	private void addIndexIncrement(AST ast, Block body, String idxName) {
        PostfixExpression inc = ast.newPostfixExpression();
        inc.setOperator(PostfixExpression.Operator.INCREMENT);
        inc.setOperand(ast.newSimpleName(idxName));

        body.statements().add(ast.newExpressionStatement(inc));
    }
    
    /**
     * Creates the condition expression for the while-loop using the iterator.
     * 
     * @param ast The AST instance.
     * @param itName The name of the iterator variable.
     * @return A {@code MethodInvocation} representing {@code it.hasNext()}.
     */
    private Expression buildHasNext(AST ast, String itName) {
        // Generates the call: itName.hasNext()
        MethodInvocation hasNext = ast.newMethodInvocation();
        hasNext.setExpression(ast.newSimpleName(itName));
        hasNext.setName(ast.newSimpleName("hasNext"));
        return hasNext;
    }

    /**
     * Adds required import declarations (Iterator, Arrays)
     * if they are not already present in the compilation unit.
     * 
     * @param cu the actual CompilationUnit
     */
    private void addMissingImports(CompilationUnit cu) {
        AST ast = cu.getAST();
        ListRewrite lr = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);

        // Add Iterator import if required and not already present
        if (needsIteratorImport && !hasImport("java.util.Iterator")) {
            ImportDeclaration imp = ast.newImportDeclaration();
            imp.setName(ast.newName("java.util.Iterator"));
            lr.insertLast(imp, null);
        }

        // Add Arrays import if required (used for Reference Array conversion)
        if (needsArraysImport && !hasImport("java.util.Arrays")) {
            ImportDeclaration imp = ast.newImportDeclaration();
            imp.setName(ast.newName("java.util.Arrays"));
            lr.insertLast(imp, null);
        }
    }

    /**
     * Signal to finalize the transformation process when the entire 
     * compilation unit has been visited.
     * 
     * @param node The CompilationUnit being finished.
     */
    @Override
    public void endVisit(CompilationUnit node) {
        addMissingImports(node);
    }
}
