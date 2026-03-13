package org.plugin.refactor.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * Final rewriting phase responsible for materializing
 * control-flow transformations previously annotated in the AST.
 *
 * <p>This visitor performs the following actions:</p>
 * <ul>
 *   <li>Replaces {@link BreakStatement} and
 *       {@link ContinueStatement} nodes with assignments
 *       to auxiliary boolean control variables.</li>
 *   <li>Introduces the necessary boolean declarations.</li>
 *   <li>Augments loop conditions to incorporate generated
 *       control flags using logical conjunction (&&).</li>
 *   <li>Ensures proper structural placement of new declarations
 *       even when the loop is not originally enclosed in a block.</li>
 * </ul>
 *
 * <p>This phase finalizes the transformation of rupture-based
 * control flow into explicit boolean-guarded execution.</p>
 */
public class LoopRewriterFinal extends ASTVisitor {

	/**
     * Rewriter used to apply structural modifications to the AST.	 
     */
    private ASTRewrite rewrite;

    /**
     * Creates the final rewriting visitor.
     *  
     * @param rewrite the ASTRewrite instance used to apply changes
     */
    public LoopRewriterFinal(ASTRewrite rewrite) {
        this.rewrite = rewrite;
    }
    
    /**
     * Replaces a break statement with an assignment that
     * sets the associated control flag to {@code false}.
     *
     * <p>The control variable name is retrieved from
     * properties attached in earlier phases.</p>
     */
    @Override
    public void endVisit(BreakStatement node) {
      	// Replace the break statement with any stayX = false
          String nombre = (String) node.getProperty("breakName");
          if (nombre == null)
          	nombre = (String) node.getProperty("breakNameFinally");
          if (nombre != null) {
              AST ast = node.getAST();
              Assignment asign = ast.newAssignment();
              asign.setLeftHandSide(ast.newSimpleName(nombre));
              asign.setRightHandSide(ast.newBooleanLiteral(false));
              asign.setOperator(Assignment.Operator.ASSIGN);
              ExpressionStatement reemplazo = ast.newExpressionStatement(asign);
              rewrite.replace(node, reemplazo, null);
          }
    }

    /**
     * Replaces a continue statement with an assignment that
     * sets the associated control flag to {@code false}.
     *
     * <p>The control variable name is retrieved from
     * properties attached in earlier phases.</p>
     */
    @Override
  	public void endVisit(ContinueStatement node) {
    	// Replace the continue statement with any keepY = false
        String nombre = (String) node.getProperty("continueName");
        if (nombre == null)
        	nombre = (String) node.getProperty("continueNameFinally");
        if (nombre != null) {
            AST ast = node.getAST();
            Assignment asign = ast.newAssignment();
            asign.setLeftHandSide(ast.newSimpleName(nombre));
            asign.setRightHandSide(ast.newBooleanLiteral(false));
            asign.setOperator(Assignment.Operator.ASSIGN);
            ExpressionStatement reemplazo = ast.newExpressionStatement(asign);
            rewrite.replace(node, reemplazo, null);
        }
    }
    

  	/**
     * Processes while loops that require flow rewriting. 
     */
    @Override
    public boolean visit(WhileStatement node) {
    	if (needsFlowRewrite(node))
    		applyFlowTransformation(node);
    	return true;
    }

	/**
	 * Processes do-while loops that require flow rewriting. 
	 */
    @Override
    public boolean visit(DoStatement node) {
    	if (needsFlowRewrite(node))
    		applyFlowTransformation(node);
    	return true;
    }

    /**
     * Determines whether a loop node requires transformation
     * based on the presence of previously attached control-flow
     * properties.
     * 
     * @param node the loop statement
     * @return true if rewriting is required
     */
    private boolean needsFlowRewrite(Statement node) {
	    return node.getProperty("breakName") != null
	        || node.getProperty("breakNamesFinally") != null
	        || node.getProperty("continueName") != null
	        || node.getProperty("continueNamesFinally") != null;
	}

    /**
     * Applies the full transformation to a loop:
     *
     * <ul>
     *   <li>Declares required boolean control variables.</li>
     *   <li>Augments the loop condition with control flags.</li>
     *   <li>Inserts continue-related variables inside the loop body.</li>
     * </ul>
     *
     * <p>This method assumes that rupture annotations have already
     * been computed in previous phases.</p>
     *
     * 
     * @param loopNode
     */
    @SuppressWarnings("unchecked")
    private void applyFlowTransformation(Statement loopNode) {
    	// Add the necessary booleans (before or after the while loop)
    	String breakName  = (String) loopNode.getProperty("breakName");
    	Set<String> breakFinallyNames = (Set<String>) loopNode.getProperty("breakNamesFinally");
    	String continueName = (String) loopNode.getProperty("continueName");
    	Set<String> continueFinallyNames = (Set<String>) loopNode.getProperty("continueNamesFinally");
      
    	if ((breakName == null) && (breakFinallyNames == null) && (continueName == null) && (continueFinallyNames == null)) 
    		return;

    	AST ast = loopNode.getAST();

    	if (breakName != null) {
    		// create: boolean stayX = true;
    		VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
    		frag.setName(ast.newSimpleName(breakName));
    		frag.setInitializer(ast.newBooleanLiteral(true));
    		VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
    		decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

    		// insert declaration before loop or wrap in a block if the parent is not one
    		insertDeclarationBefore(loopNode, decl);
    	}

    	if (breakFinallyNames != null) {
    		for (String name : breakFinallyNames) {
    			VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
    			frag.setName(ast.newSimpleName(name));
    			frag.setInitializer(ast.newBooleanLiteral(true));
    			VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
    			decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
    			insertDeclarationBefore(loopNode, decl);
    		}
    	}

    	/*
    	 * this changes the while conditions
    	 */
    	if ((breakName != null) || (breakFinallyNames != null)) {
	        Expression originalCond = null;
	        if (loopNode instanceof WhileStatement) {
	            originalCond = ((WhileStatement) loopNode).getExpression();
	        } else if (loopNode instanceof DoStatement) {
	            originalCond = ((DoStatement) loopNode).getExpression();
	        }
	        
	        ParenthesizedExpression paren = ast.newParenthesizedExpression();
	        paren.setExpression((Expression) rewrite.createCopyTarget(originalCond));
	        Expression ed = paren;
	        InfixExpression newCond = null;
	
	        List<String> nombres = new ArrayList<String>();
	        if (breakName != null) {
	        	nombres.add(breakName);
	        }
	        if (breakFinallyNames != null) {
	        	for (String s: breakFinallyNames) {
	        		nombres.add(s);        		
	        	} 
	        }
	
	        if (nombres.size() > 0) {
	        	int i = nombres.size();
	        	while (i > 0) {
	        		i--;
	        		SimpleName sn = ast.newSimpleName(nombres.get(i));
	        		newCond = ast.newInfixExpression();
	        		newCond.setRightOperand(ed);
	        		newCond.setLeftOperand(sn);
	        		newCond.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
	        		ed = newCond;
	        	}
	        	rewrite.replace(originalCond, newCond, null);
	        }
    	}

    	/*
    	 * CONTINUE management ------------
    	 */
    	if (continueName != null) {
    		VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
    		frag.setName(ast.newSimpleName(continueName));
    		frag.setInitializer(ast.newBooleanLiteral(true));
    		VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
    		decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

    		insertDeclarationFirst(loopNode, decl);
    	}

    	if (continueFinallyNames != null) {
    		for (String name : continueFinallyNames) {
    			VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
    			frag.setName(ast.newSimpleName(name));
    			frag.setInitializer(ast.newBooleanLiteral(true));
    			VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
    			decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
    			insertDeclarationFirst(loopNode, decl);
    		}
    	}
    }

    /**
     * Inserts a declaration as the first statement
     * inside the loop body.
     *
     * <p>This is primarily used for continue-related
     * control flags.</p>
     * 
     * @param loopNode
     * @param decl
     */
    private void insertDeclarationFirst(Statement loopNode, VariableDeclarationStatement decl) {
    	ASTNode body = getBody(loopNode);
    	if (body != null) {
    		// The body is already a block { ... }
    		ListRewrite lr = rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY);
    		lr.insertFirst(decl, null);
    	} 
    }

    /**
     * Inserts a variable declaration before the loop.
     * 
     * <p>If the loop is not already enclosed in a block,
     * a new block is created to preserve syntactic correctness.</p>
     * 
     * @param loopNode
     * @param decl
     */
    @SuppressWarnings("unchecked")
    private void insertDeclarationBefore(Statement loopNode, VariableDeclarationStatement decl) {
	    AST ast = loopNode.getAST();
	    ASTNode parent = loopNode.getParent();

	    if (parent instanceof Block) {
	        ListRewrite listRewrite = rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
	        listRewrite.insertBefore(decl, loopNode, null);
	    } else {
	        // We create the block that will wrap the if without braces.
	        Block newBlock = ast.newBlock();
	        
	        // 1. We add the declaration
	        newBlock.statements().add(decl); 
	        
	        // 2. copy the statement
	        Statement moveTarget = (Statement) rewrite.createMoveTarget(loopNode);
	        newBlock.statements().add(moveTarget);
	        
	        // 3. We replaced the original loop (which was under the 'if') 
	        //    with the new block
	        rewrite.replace(loopNode, newBlock, null);
	    }
	}  
  
    /**
     * Retrieves the block body of a loop if present.  
     *  
     * @param node a loop statement
     * @return the loop body block or null
     */
    public Block getBody(Statement node) {
    	Block block = null;
    	if (node instanceof WhileStatement ws && ws.getBody() instanceof Block)
    		block = (Block) ws.getBody();
    	if (node instanceof DoStatement ds && ds.getBody() instanceof Block)
    		block = (Block) ds.getBody();

    	return block;
    }
}
