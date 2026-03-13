package org.plugin.refactor.code;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * AST visitor responsible for annotating control-flow rupture
 * statements ({@link BreakStatement} and {@link ContinueStatement})
 * in order to prepare them for structural transformation.
 *
 * <p>The visitor performs the following tasks:</p>
 * <ul>
 *   <li>Identifies break and continue statements inside loops.</li>
 *   <li>Generates unique auxiliary boolean variable names
 *       (e.g., {@code stay}, {@code keep}).</li>
 *   <li>Associates these names with the enclosing loop.</li>
 *   <li>Marks the immediately following statement so it can be
 *       conditionally guarded during rewriting.</li>
 *   <li>Handles special cases where rupture statements appear
 *       inside {@code finally} blocks.</li>
 * </ul>
 *
 * <p>This visitor does not perform transformations directly;
 * it only annotates AST nodes with custom properties used
 * later by rewriting visitors.</p>
 * 
 */
public class FlowAnnotator extends ASTVisitor {
	
    private final Set<String> usedNames = new HashSet<>();
    private final Set<String> existingNames = new HashSet<>(); // variables/parametros ya declarados

    /**
     * Creates a new flow annotator and collects existing
     * variable and parameter names from the compilation unit
     * to prevent name collisions when generating auxiliary flags.
     * 
     * @param cu the compilation unit being analyzed
     */
    public FlowAnnotator(CompilationUnit cu) {
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
     * Processes a break statement and associates it with
     * an auxiliary control flag if it appears inside a
     * conditional context within a loop.
     */
    @SuppressWarnings("unchecked")
	@Override
     public boolean visit(BreakStatement node) {
    	boolean hasIf = false;
    	if (node.getLabel() != null) // labeled break 
    		return true;
    	
        ASTNode actual = node.getParent();
        while (actual != null 
                && !(actual instanceof WhileStatement) 
                && !(actual instanceof DoStatement) 
                && !(actual instanceof SwitchStatement)
                && !(actual instanceof EnhancedForStatement)) {
        	if (actual instanceof IfStatement)
        		hasIf = true;
            actual = actual.getParent();
        }

        if (actual == null || actual instanceof SwitchStatement || actual instanceof EnhancedForStatement) {
            return true; // break of switch or ForEach without solution
        }
        
        if (!hasIf) {
        	return true;
        }
        ASTNode loopParent = actual;
        String name;
        Set<String> names;
        if (node.getProperty("breakInsideFinally") != null) {
            names = (Set<String>) loopParent.getProperty("breakNamesFinally");
            name = createName("stay");            
            if (names == null) {
            	names = new HashSet<String>();
            }
            names.add(name);
            loopParent.setProperty("breakNamesFinally", names);
            node.setProperty("breakNameFinally", name);
            markNextStatement(node, name);            
        }
        else {
        	
	        name = (String) loopParent.getProperty("breakName");
	        if (name == null) {
	            name = createName("stay");
	            loopParent.setProperty("breakName", name);
	        }
	        node.setProperty("breakName", name);
            markNextStatement(node, name);        
        }
        return true; 
    }

    
    /**
     * Processes a continue statement and associates it with
     * an auxiliary control flag if it appears inside a
     * conditional context within a loop.
     */
    @SuppressWarnings("unchecked")
	@Override
    public boolean visit(ContinueStatement node) {

    	if (node.getLabel() != null) // labeled continue
    		return true;
    	boolean hasIf = false;
        ASTNode actual = node.getParent();
        while (actual != null 
                && !(actual instanceof WhileStatement) 
                && !(actual instanceof DoStatement) 
                && !(actual instanceof SwitchStatement)) {
        	if (actual instanceof IfStatement)
        		hasIf = true;
            actual = actual.getParent();
        }
        
        if (actual == null || actual instanceof SwitchStatement || actual instanceof EnhancedForStatement) {
            return true; 
        }

        if (!hasIf) {
        	return true;
        }
        ASTNode loopParent = actual;
        String name;
        Set<String> names;
        if (node.getProperty("continueInsideFinally") != null) {
            names = (Set<String>) loopParent.getProperty("continueNamesFinally");
            name = createName("keep");            
            if (names == null) {
            	names = new HashSet<String>();
            }
            names.add(name);
            loopParent.setProperty("continueNamesFinally", names);
           
            node.setProperty("continueNameFinally", name);
            markNextStatement(node, name);            
        }
        else {
	        name = (String) loopParent.getProperty("continueName");
	        if (name == null) {
	            name = createName("keep");
	            loopParent.setProperty("continueName", name);
	        }
	        node.setProperty("continueName", name);
            markNextStatement(node, name);        
        }
        return true; 
    }
    

    /**
     * Generates a unique variable name based on the provided base,
     * avoiding collisions with both previously generated names
     * and existing identifiers in the compilation unit.
     * 
     * @param base the base prefix (e.g., "stay", "keep")
     * @return a collision-free identifier
     */
    private String createName(String base) {
        int i = 1;
        String candidate;
        do {
            candidate = base + i++;
        } while (usedNames.contains(candidate) || existingNames.contains(candidate));
        usedNames.add(candidate);
        return candidate;
    }
    
    /**
     * Marks the next executable statement following a rupture
     * statement within the same block. The marked statement
     * receives a custom property indicating that it must be
     * conditionally guarded by the associated control flag.
     *
     * <p>The traversal continues upward until the enclosing
     * loop structure is reached.</p>
     * 
     * @param ruptureStmt the break or continue statement
     * @param name the auxiliary control variable name
     */
    @SuppressWarnings("unchecked")
    private void markNextStatement(Statement ruptureStmt, String name) {
        ASTNode parent = ruptureStmt.getParent();
        ASTNode son = ruptureStmt;
        boolean end = false;
        Set<String> moves;
        
        while (parent != null && !end) {
        	
        	end =   (parent instanceof WhileStatement 
                    || parent instanceof DoStatement 
                    || parent instanceof ForStatement 
                    || parent instanceof EnhancedForStatement);

        	if (!end) {
                if (parent instanceof Block block) {
                    List<Statement> stmts = (List<Statement>) block.statements();
                    int index = stmts.indexOf(son);

                    // TODO: ver esto -------------------------------
                    // CAMBIO AQUÍ: Solo marcamos la SIGUIENTE, no todas
                    if (index >= 0 && index < (stmts.size() - 1)) {
                        Statement nextStmt = stmts.get(index + 1);
                        moves = (HashSet<String>) nextStmt.getProperty("moves");
                        if (moves == null) {
                            moves = new HashSet<String>();
                        }
                        moves.add(name);
                        nextStmt.setProperty("moves", moves);
                        
                        // IMPORTANTE: Una vez que marcamos la primera del bloque, 
                        // ya no necesitamos seguir marcando hermanos en este nivel.
                    }
            	}
                if (parent instanceof CatchClause catchClause) {
                    Block body = catchClause.getBody();
                    List<Statement> statements = (List<Statement>) body.statements();
                    int idx = statements.indexOf(son);

                    // TODO: dejar igual que el bloque anterior: sacar el for
                    if (idx >= 0 && idx < (statements.size() - 1)) {
                        for (int i = idx + 1; i < statements.size(); i++) {
                        	moves = (HashSet<String>) statements.get(idx).getProperty("moves");
                        	if (moves == null) {
                        		moves = new HashSet<String>();
                        	}
                        	moves.add(name);
                            statements.get(idx).setProperty("moves", moves);
                        }
                    } 
                }
                son = parent;
                parent = son.getParent();
        	}
        }
        
    }
}
