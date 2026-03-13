package org.plugin.refactor.code;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * First rewriting phase for loop bodies.
 *
 * <p>This visitor restructures block statements by grouping
 * statements affected by control-flow rupture annotations
 * (e.g., break/continue) into conditional {@link IfStatement}
 * wrappers.</p>
 *
 * <p>The algorithm processes each {@link Block} bottom-up,
 * accumulating statements and wrapping them inside newly
 * generated {@code if} statements whenever a "move" annotation
 * is detected.</p>
 *
 * <p>This phase does not create or resolve control flags;
 * it only restructures the AST based on properties previously
 * attached by flow-annotation visitors.</p>
 * 
 */
public class LoopRewriterFirst extends ASTVisitor {

	/**
     * Rewriter used to apply structural modifications to the AST.	 
     */
    private ASTRewrite rewrite;

    /**
     * Creates a new rewriter phase.
     *  
     * @param rewrite the ASTRewrite instance used to apply structural changes
     */
    public LoopRewriterFirst(ASTRewrite rewrite) {
        this.rewrite = rewrite;
    }
    
    /**
     * Rewrites a block by grouping statements according to
     * previously attached control-flow annotations.
     *
     * <p>The block is traversed bottom-up. Statements that carry
     * a "moves" property (or special labeled markers) trigger
     * the creation of a new {@code if} statement that wraps the
     * accumulated statement group.</p>
     *
     * <p>The final reconstructed block replaces the original
     * list of statements using {@link ListRewrite}.</p>
     */
    @SuppressWarnings("unchecked")
	public boolean visit(Block node) {
        List<Statement> stmts = node.statements();
        if (stmts.isEmpty()) return true;

        AST ast = node.getAST();
        // List where we will rebuild the block
        List<Statement> currentGroup = new ArrayList<>();
        
        // We go from bottom to top
        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);
            Statement nodeToProcess = stmt;
            Set<String> moves = null;

            // 1. BRAND TREATMENT (Label)
            if (stmt instanceof LabeledStatement) {
                LabeledStatement labeled = (LabeledStatement) stmt;
                String labelName = labeled.getLabel().getIdentifier();
                if (labelName.startsWith("MARK_")) {
                    nodeToProcess = (Statement) rewrite.createMoveTarget(labeled.getBody());
                    if (labelName.equals("MARK_hasBreak")) {
                        List<String> names = getBreakNames(stmt);
                        moves = new HashSet<>(names);
                    }
                }
            } else {
                moves = (Set<String>) stmt.getProperty("moves");
            }

            // 2. GROUPING LOGIC
            if (moves != null && !moves.isEmpty()) {
                // "nodeToProcess" it is the sentence that has the move (ex: break or i++)
                Statement targetNode = (nodeToProcess == stmt) 
                    ? (Statement) rewrite.createMoveTarget(stmt) 
                    : nodeToProcess;

                // We added the current sentence to the group that came from below.
                currentGroup.add(0, targetNode);

                // We created the IF statement that wrapping everything accumulated so far
                IfStatement ifs = ast.newIfStatement();
                ifs.setExpression(buildCondition(ast, new ArrayList<>(moves)));
                
                Block thenBlock = ast.newBlock();
                for (Statement s : currentGroup) {
                    thenBlock.statements().add(s);
                }
                ifs.setThenStatement(thenBlock);

                /*
                 * We reset the group: now the current group is only the IF statement, 
                 * and the statements that come ABOVE will be added 
                 * before this IF statement.
                 */
                currentGroup = new ArrayList<>();
                currentGroup.add(ifs);
            } else {
                // Normal sentence (or continue i++): 
            	// We add it to the beginning of the current group
                Statement target = (nodeToProcess == stmt) 
                    ? (Statement) rewrite.createMoveTarget(stmt) 
                    : nodeToProcess;
                currentGroup.add(0, target);
            }
        }

        // 3. REPLACEMENT IN THE AST
        ListRewrite lr = rewrite.getListRewrite(node, Block.STATEMENTS_PROPERTY);
        // clean original
        for (Statement s : stmts) lr.remove(s, null);
        // insert the result (currentGroup already has the correct orden)
        for (Statement s : currentGroup) lr.insertLast(s, null);

        return true;
    }    

    /**
     * Retrieves all auxiliary control variable names associated
     * with break statements in the enclosing loop.
     *
     * <p>This includes both regular break flags and those
     * originating from {@code finally} blocks.</p>
     * 
     * @param stmt a statement inside the loop
     * @return list of control flag identifiers
     */
    @SuppressWarnings("unchecked")
	private List<String> getBreakNames(Statement stmt) {
    	List<String> breakNames = new ArrayList<>();
    	ASTNode parent = getParent(stmt);
    	if (parent.getProperty("breakName") != null) {
    		breakNames.add((String)parent.getProperty("breakName"));
    	}
    	Set<String> set = (Set<String>)parent.getProperty("breakNamesFinally");
    	if ((set != null) && !(set.isEmpty())) {
    		for (String s: set)
    			breakNames.add(s);
    	}
    	return breakNames;
    }
    
    /**
     * Finds the nearest enclosing loop (currently WhileStatement).
     *  
     * @param stmt the starting statement
     * @return the enclosing loop node, or null if none is found
     */
    private ASTNode getParent(Statement stmt) {
    	ASTNode parent = stmt.getParent();
    	while ((parent != null) &&
    		  !(parent instanceof WhileStatement) &&
    		  !(parent instanceof DoStatement)) {
    		parent = parent.getParent();
    	}
    	return parent;
    }
    
    /**
     * Builds a logical AND condition combining all provided
     * control flag names.
     *
     * <p>If only one name is present, a simple name expression
     * is returned. Otherwise, a left-associative chain of
     * {@code &&} expressions is constructed.</p>
     * 
     * @param ast the AST instance used to create nodes
     * @param names the control flag identifiers
     * @return the resulting boolean expression
     */
    private Expression buildCondition(AST ast, List<String> names) {
        if (names.size() == 1) {
            return ast.newSimpleName(names.get(0));
        }
        int idx = names.size() - 1;
        Expression ed = ast.newSimpleName(names.get(idx));
        while (idx > 0) {
            idx--;
            InfixExpression inf = ast.newInfixExpression();
            inf.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
            inf.setLeftOperand(ast.newSimpleName(names.get(idx)));
            inf.setRightOperand(ed);
            ed = inf;
        }
        return ed;
    }
}
