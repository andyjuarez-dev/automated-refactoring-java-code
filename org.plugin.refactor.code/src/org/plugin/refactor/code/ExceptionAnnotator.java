package org.plugin.refactor.code;

import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * AST visitor that annotates {@link BreakStatement} and {@link ContinueStatement}
 * nodes when they appear inside a {@code finally} block.
 *
 * <p>This visitor is used to detect control-flow interruptions occurring within
 * {@link TryStatement} finally sections, which may require special handling
 * during refactoring to preserve semantic correctness.</p>
 *
 * <p>It also collects existing variable and parameter names from the compilation
 * unit to prevent name collisions when introducing auxiliary variables.</p>
 * 
 */
public class ExceptionAnnotator extends ASTVisitor {

	/**
     * Set of already declared variable and parameter names within the compilation unit.
     * Used to avoid identifier collisions during transformation.
	 */
	private final Set<String> existingNames = new HashSet<>();

	/**
     * Creates a new annotator and collects all existing variable and parameter names
     * from the given compilation unit.
	 * 
	 * @param cu the compilation unit to analyze
	 */
    public ExceptionAnnotator(CompilationUnit cu) {
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                existingNames.add(node.getName().getIdentifier());
                return true;
            }

            @Override
            public boolean visit(SingleVariableDeclaration node) {
                existingNames.add(node.getName().getIdentifier());
                return true;
            }
        });
    }

    /**
     * Traverses the "finally" block of a try statement to identify and mark 
     * break and continue statements.
     * @param node The TryStatement being visited.
     * @return true to continue visiting the AST.
     */
    @Override
     public boolean visit(TryStatement node) {
    	Block blockFinally = node.getFinally();
        // If a finally block exists, visit its children to find control flow statements
    	if (blockFinally != null) {
    	    blockFinally.accept(new ASTVisitor() {
    	        @Override
    	        public boolean visit(BreakStatement breakStmt) {
    	        	if (breakStmt.getLabel() != null) // labeled break
    	        		return true;

    	        	breakStmt.setProperty("breakInsideFinally", true);
    	            return super.visit(breakStmt);
    	        }
    	        
    	        @Override
    	        public boolean visit(ContinueStatement continueStmt) {
    	        	if (continueStmt.getLabel() != null) // labeled continue 
    	        		return true;

    	            continueStmt.setProperty("continueInsideFinally", true);
    	        	return super.visit(continueStmt);
    	        }
    	    });
    	}
        return true;
    }
}
