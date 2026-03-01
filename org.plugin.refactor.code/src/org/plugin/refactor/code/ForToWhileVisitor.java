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

public class ForToWhileVisitor extends ASTVisitor {

	private final ASTRewrite rewriter;

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
        
        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof ForStatement && stmt.getProperty("change") != null) {
                ForStatement forStmt = (ForStatement) stmt;
                WhileStatement whileNode = ast.newWhileStatement();
                
                // 1. Condición
                Expression cond = forStmt.getExpression() != null 
                    ? (Expression) ASTNode.copySubtree(ast, forStmt.getExpression()) 
                    : ast.newBooleanLiteral(true);
                whileNode.setExpression(cond);

                // 2. Cuerpo
                Block whileBody = ast.newBlock();
                Statement movedBody = (Statement) rewriter.createMoveTarget(forStmt.getBody());
                whileBody.statements().add(movedBody);

                String data;
                if (forStmt.getProperty("hasBreak") != null)
                	data = "hasBreak";
                else
                	data = "hasContinue";
                	
                for (Object updater : forStmt.updaters()) {
                    Expression expr = (Expression) ASTNode.copySubtree(ast, (Expression) updater);
                    ExpressionStatement updaterStmt = ast.newExpressionStatement(expr);
                    
                    // Creamos la etiqueta como "contenedor" de la sentencia
                    LabeledStatement labeled = ast.newLabeledStatement();
                    
                    // El nombre de la etiqueta lleva la información (hasBreak o hasContinue)
                    String labelName = "MARK_" + data; // Quedará MARK_hasBreak o MARK_hasContinue
                    labeled.setLabel(ast.newSimpleName(labelName));
                    
                    // Metemos el i++ dentro de la etiqueta
                    labeled.setBody(updaterStmt);
                    
                    // Añadimos la etiqueta al bloque del while
                    whileBody.statements().add(labeled);
                }
                
                whileNode.setBody(whileBody);

                // 3. Reemplazo e Inicializadores
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
    

    // Método auxiliar para convertir 'int i=0' de expresión a sentencia
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
