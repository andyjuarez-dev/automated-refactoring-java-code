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
 * AST visitor that identifies {@link ForStatement} nodes containing
 * non-labeled {@link BreakStatement} or {@link ContinueStatement}
 * instructions that require transformation.
 *
 * <p>The visitor traverses the AST and, when encountering a break or
 * continue statement, searches for its closest enclosing loop. If the
 * loop is a {@code for} statement and the control-flow interruption
 * occurs within a conditional structure, the loop is marked for
 * subsequent refactoring.</p>
 *
 * <p>Marked {@code for} statements are annotated using custom AST
 * properties to indicate whether they contain {@code break} and/or
 * {@code continue} statements.</p>
 * 
 */
public class ForMarkerVisitor extends ASTVisitor {
	
	/**
     * Set of {@link ForStatement} nodes identified as requiring transformation.	 
     */
    private final Set<ForStatement> markedFors; 
    
    /**
     * Creates a new visitor with an empty set of marked {@code for} statements.
     */
    public ForMarkerVisitor() { 
    	markedFors = new HashSet<>();
    }

    @Override
    public boolean visit(BreakStatement node) {
        // Ignore labeled break statements
    	if (node.getLabel() != null) 
    		return true;

        markForParent(node, "hasBreak");
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        // Ignore labeled continue statements
    	if (node.getLabel() != null) 
    		return true;

        markForParent(node, "hasContinue");
        return super.visit(node);
    }

    /**
     * Searches for the closest enclosing loop of the given statement.
     * If the enclosing structure is a {@link ForStatement} and the
     * break/continue appears within a conditional context, the loop
     * is marked with the corresponding property.
     * 
     * @param stmt the break or continue statement
     * @param property the property name to associate with the loop
     */
    private void markForParent(Statement stmt, String property) {
        ASTNode parent = stmt.getParent();
        boolean hasIf = false;
        while    (parent != null && 
        		!(parent instanceof ForStatement) && 
        		!(parent instanceof EnhancedForStatement) &&
        		!(parent instanceof WhileStatement) && 
        		!(parent instanceof DoStatement)) {
        	if (parent instanceof IfStatement) 
        		hasIf = true;
            parent = parent.getParent();
        }
        if (!hasIf) {
            // Degenerate break/continue not guarded by a conditional
        	return;
        }
        if (parent instanceof ForStatement fs) {
            markedFors.add(fs);
            fs.setProperty("change", true); 
            fs.setProperty(property, true);
        }
    }

    /**
     * Returns the set of {@link ForStatement} nodes marked for transformation.     
     *  
     * @return the set of marked for statements
     */
    public Set<ForStatement> getMarkedFors() {
        return markedFors;
    }
}
