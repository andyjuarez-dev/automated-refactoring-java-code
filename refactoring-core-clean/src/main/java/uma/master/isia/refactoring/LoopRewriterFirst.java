package uma.master.isia.refactoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

public class LoopRewriterFirst extends ASTVisitor {

    private ASTRewrite rewrite;

    public LoopRewriterFirst(ASTRewrite rewrite) {
        this.rewrite = rewrite;
    }
    
    @SuppressWarnings("unchecked")
	public boolean visit(Block node) {
        List<Statement> stmts = node.statements();
        if (stmts.isEmpty()) return true;

        AST ast = node.getAST();
        // Lista donde iremos reconstruyendo el bloque
        List<Statement> currentGroup = new ArrayList<>();
        
        // Recorremos de abajo hacia arriba
        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);
            Statement nodeToProcess = stmt;
            Set<String> moves = null;

            // 1. TRATAMIENTO DE LA MARCA (Etiqueta)
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

            // 2. LÓGICA DE AGRUPAMIENTO
            if (moves != null && !moves.isEmpty()) {
                // "nodeToProcess" es la sentencia que tiene el move (ej: el break o el i++ con marca)
                Statement targetNode = (nodeToProcess == stmt) 
                    ? (Statement) rewrite.createMoveTarget(stmt) 
                    : nodeToProcess;

                // Añadimos la sentencia actual al grupo que venía de abajo
                currentGroup.add(0, targetNode);

                // Creamos el IF que envuelve todo lo acumulado hasta ahora
                IfStatement ifs = ast.newIfStatement();
                ifs.setExpression(buildCondition(ast, new ArrayList<>(moves)));
                
                Block thenBlock = ast.newBlock();
                for (Statement s : currentGroup) {
                    thenBlock.statements().add(s);
                }
                ifs.setThenStatement(thenBlock);

                // Reiniciamos el grupo: ahora el grupo actual es solo el IF
                // y las sentencias que vengan ARRIBA se irán añadiendo antes de este IF
                currentGroup = new ArrayList<>();
                currentGroup.add(ifs);
            } else {
                // Sentencia normal (o i++ de continue): la añadimos al principio del grupo actual
                Statement target = (nodeToProcess == stmt) 
                    ? (Statement) rewrite.createMoveTarget(stmt) 
                    : nodeToProcess;
                currentGroup.add(0, target);
            }
        }

        // 3. REEMPLAZO EN EL AST
        ListRewrite lr = rewrite.getListRewrite(node, Block.STATEMENTS_PROPERTY);
        // Limpiar original
        for (Statement s : stmts) lr.remove(s, null);
        // Insertar resultado (currentGroup ya tiene el orden correcto)
        for (Statement s : currentGroup) lr.insertLast(s, null);

        return true;
    }    
    
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
    
    private ASTNode getParent(Statement stmt) {
    	ASTNode parent = stmt.getParent();
    	while ((parent != null) &&
    			!(parent instanceof WhileStatement)) {
    		parent = parent.getParent();
    	}
    	return parent;
    }
    
    private Expression buildCondition(AST ast, List<String> names) {
//    	System.out.println("\n**************** ARMO LA CONDICION ****************************************\n");
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
