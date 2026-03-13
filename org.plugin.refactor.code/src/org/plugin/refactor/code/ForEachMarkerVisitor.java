package org.plugin.refactor.code;

import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.ASTVisitor;

/**
 * AST visitor that identifies {@link EnhancedForStatement} nodes
 * containing non-labeled {@link BreakStatement} or
 * {@link ContinueStatement} instructions that require transformation.
 *
 * <p>The visitor traverses the AST and, upon encountering a break
 * or continue statement, searches for its closest enclosing loop.
 * If the enclosing structure is an {@code enhanced for} statement
 * and the control-flow interruption occurs within a conditional
 * context, the loop is marked for subsequent conversion.</p>
 *
 * <p>Marked loops are annotated using the custom AST property
 * {@code "change"}.</p>
 * 
 */
public class ForEachMarkerVisitor extends ASTVisitor {

	/**
     * Set of {@link EnhancedForStatement} nodes identified
     * as requiring transformation.
	 */
    private final Set<EnhancedForStatement> markedFors;

    /**
     * Creates a new visitor with an empty set of marked
     * enhanced for statements.
     */
    public ForEachMarkerVisitor() {
    	markedFors = new HashSet<>();
    }
    

    @Override
    public boolean visit(BreakStatement node) {
        // Ignore labeled break statements
    	if (node.getLabel() != null) 
    		return true;

        markForEachParent(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        // Ignore labeled continue statements
    	if (node.getLabel() != null) 
    		return true;

        markForEachParent(node);
        return super.visit(node);
    }

    /**
     * Traverses the parent chain of the given statement until the
     * closest enclosing loop is found. If the enclosing loop is an
     * {@link EnhancedForStatement} and the break/continue appears
     * within a conditional structure, the loop is marked for
     * conversion.
     * 
     * @param stmt the break or continue statement
     */
    private void markForEachParent(Statement stmt) {
        ASTNode parent = stmt.getParent();
        boolean hasIf = false;
        while (parent != null && !(parent instanceof ForStatement)
        		&& !(parent instanceof WhileStatement) && !(parent instanceof DoStatement)
        		&& !(parent instanceof EnhancedForStatement)) {
        	if (parent instanceof IfStatement) 
        		hasIf = true;
        	parent = parent.getParent();
        }
        if (!hasIf) {
            // Degenerate break/continue not guarded by a conditional
        	return;
        }
        if (parent instanceof EnhancedForStatement efs) {
            markedFors.add(efs);
            efs.setProperty("change", true);
        }
    }

    /**
     * Returns the set of {@link EnhancedForStatement}
     * nodes marked for transformation.
     * 
     * @return the set of marked enhanced for statements
     */
    public Set<EnhancedForStatement> getMarkedFors() {
        return markedFors;
    }
}
