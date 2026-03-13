package org.plugin.refactor.code;

import java.util.List;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.dom.Expression;

/**
 * AST visitor responsible for transforming {@link ForStatement} nodes
 * previously marked for modification into semantically equivalent
 * {@link WhileStatement} structures.
 *
 * <p>The transformation preserves the original execution semantics by:</p>
 * <ul>
 *   <li>Moving the loop condition to the {@code while} expression.</li>
 *   <li>Relocating the original loop body inside the new {@code while} block.</li>
 *   <li>Appending updater expressions at the end of the loop body.</li>
 *   <li>Extracting initializer expressions and placing them before the loop.</li>
 * </ul>
 *
 * <p>Updater expressions are wrapped inside labeled statements to preserve
 * control-flow handling required by subsequent refactoring steps
 * (e.g., break/continue elimination).</p>
 *
 * <p>The transformation is applied only to {@code for} statements annotated
 * with the custom property {@code "change"}.</p>
 * 
 */
public class ForToWhileVisitor extends ASTVisitor {

	/**
     * Rewriter used to apply structural modifications to the AST.	 
     */
	private final ASTRewrite rewriter;

	/**
     * Creates a new visitor that performs {@code for}-to-{@code while}
     * transformations using the provided {@link ASTRewrite}.
	 * 
	 * @param rewriter the AST rewriter used to apply code transformations
	 */
    public ForToWhileVisitor(ASTRewrite rewriter) {
        this.rewriter = rewriter;
    }
    
    @SuppressWarnings("unchecked")    
    @Override
    public boolean visit(Block node) {
		List<Statement> stmts = node.statements();
        if (stmts.isEmpty()) return true;

        AST ast = node.getAST();
        ListRewrite lr = rewriter.getListRewrite(node, Block.STATEMENTS_PROPERTY);

        // Iterate backwards to avoid index shifting during replacements
        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof ForStatement && stmt.getProperty("change") != null) {
                ForStatement forStmt = (ForStatement) stmt;
                WhileStatement whileNode = ast.newWhileStatement();
                
                /*
                 * 1. Condition transformation:
                 * If the original for-loop has no condition,
                 * it is treated as an infinite loop (while true).
                 */
                Expression cond = forStmt.getExpression() != null 
                    ? (Expression) ASTNode.copySubtree(ast, forStmt.getExpression()) 
                    : ast.newBooleanLiteral(true);
                whileNode.setExpression(cond);

                /*
                 * 2. Body transformation:
                 * The original body is moved into a new block
                 * representing the body of the while loop.
                 */
                Block whileBody = ast.newBlock();
                Statement movedBody = (Statement) rewriter.createMoveTarget(forStmt.getBody());
                whileBody.statements().add(movedBody);

                /*
                 * 3. Updater handling:
                 * Each updater expression is appended at the end
                 * of the while body and wrapped in a labeled statement.
                 */
                String data;
                if (forStmt.getProperty("hasBreak") != null)
                	data = "hasBreak";
                else
                	data = "hasContinue";
                	
                for (Object updater : forStmt.updaters()) {
                    Expression expr = (Expression) ASTNode.copySubtree(ast, (Expression) updater);
                    ExpressionStatement updaterStmt = ast.newExpressionStatement(expr);
                    LabeledStatement labeled = ast.newLabeledStatement();
                    String labelName = "MARK_" + data; // It'll be MARK_hasBreak or MARK_hasContinue
                    labeled.setLabel(ast.newSimpleName(labelName));
                    labeled.setBody(updaterStmt);
                    whileBody.statements().add(labeled);
                }
                whileNode.setBody(whileBody);

                /*
                 * 4. Initializer extraction:
                 * If the for-loop contains initializers,
                 * they are extracted and placed before the loop.
                 */
                if (forStmt.initializers().isEmpty()) {
                    lr.replace(stmt, whileNode, null);
                } else {
                    Block replacementBlock = ast.newBlock();
                    for (Object init : forStmt.initializers()) {
                        if (init instanceof VariableDeclarationExpression) {
                            replacementBlock.statements().add(transformToStatement((VariableDeclarationExpression) init, ast));
                        } else if (init instanceof Expression) {
                            replacementBlock.statements().add(ast.newExpressionStatement(
                                (Expression) ASTNode.copySubtree(ast, (Expression) init)));
                        }
                    }
                    replacementBlock.statements().add(whileNode);
                    lr.replace(stmt, replacementBlock, null);
                }
            }
        }
        return true;
    }
    

    /**
     * Converts a {@link VariableDeclarationExpression} (used in a {@code for} initializer) 
     * into an equivalent {@link VariableDeclarationStatement} 
     * so it can be placed outside the loop.
     * 
     * @param vde the variable declaration expression
     * @param ast the current AST instance
     * @return a corresponding variable declaration statement
     */
    private VariableDeclarationStatement transformToStatement(VariableDeclarationExpression vde, AST ast) {
        VariableDeclarationFragment oldFrag = (VariableDeclarationFragment) vde.fragments().get(0);
        VariableDeclarationFragment newFrag = ast.newVariableDeclarationFragment();
        newFrag.setName(ast.newSimpleName(oldFrag.getName().getIdentifier()));
        if (oldFrag.getInitializer() != null) {
            newFrag.setInitializer((Expression) ASTNode.copySubtree(ast, oldFrag.getInitializer()));
        }
        VariableDeclarationStatement vds = ast.newVariableDeclarationStatement(newFrag);
        vds.setType((org.eclipse.jdt.core.dom.Type) ASTNode.copySubtree(ast, vde.getType()));
        return vds;
    }
}
