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

public class FlowAnnotator extends ASTVisitor {
	
    private final Set<String> usedNames = new HashSet<>();
    private final Set<String> existingNames = new HashSet<>(); // variables/parametros ya declarados

    public FlowAnnotator(CompilationUnit cu) {
        // Recolectar nombres existentes para evitar colisiones 
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

    @SuppressWarnings("unchecked")
	@Override
     public boolean visit(BreakStatement node) {
        // buscar el while o do-while padre mas cercano
    	//boolean finallyBreak = false;
    	boolean hayIf = false;
//    	System.out.println("anoto los break");
    	if (node.getLabel() != null) // break etiquetado
    		return true;
    	
        ASTNode actual = node.getParent();
        while (actual != null 
                && !(actual instanceof WhileStatement) 
                && !(actual instanceof DoStatement) 
                && !(actual instanceof SwitchStatement)
                && !(actual instanceof EnhancedForStatement)) {
        	if (actual instanceof IfStatement)
        		hayIf = true;
            actual = actual.getParent();
        }

        if (actual == null || actual instanceof SwitchStatement || actual instanceof EnhancedForStatement) {
            return true; // es un break de switch o de ForEach sin resolver, no se procesa
        }
        
        if (!hayIf) {
  //      	System.out.println("No depende de un if");
        	return true;
        }
        ASTNode loopParent = actual;
        String name;
        Set<String> names;
        if (node.getProperty("breakInsideFinally") != null) {
        //	System.out.println("------------------- Es un break de finally ---------------\n");
//        	System.out.println(node.getParent());
        	
            names = (Set<String>) loopParent.getProperty("breakNamesFinally");
            name = createName("stay");            
            if (names == null) {
            	names = new HashSet<String>();
            }
            names.add(name);
            loopParent.setProperty("breakNamesFinally", names);
            
            // marcar el break y las sentencias posteriores
            node.setProperty("breakNameFinally", name);
            markFollowingStatements(node, name);            
        	//System.out.println("------------------- Es un break de finally ANOTADO ---------------\n");
        }
        else {
        	//System.out.println("------------------- Es un break normal ---------------\n");
        	//System.out.println(node.getParent());
        	
	        name = (String) loopParent.getProperty("breakName");
	        if (name == null) {
	            name = createName("stay");
	            loopParent.setProperty("breakName", name);
	        }
	
	        // marcar el break y las sentencias posteriores
	        node.setProperty("breakName", name);
            markFollowingStatements(node, name);        
	        //System.out.println("------------------- Es un break de normal ANOTADO ---------------\n");
	        
        }
        return true; // se sigue recorriendo
    }

    
    @SuppressWarnings("unchecked")
	@Override
    public boolean visit(ContinueStatement node) {

        // buscar el while o do-while padre mas cercano
//    	System.out.println("anoto los continue");
    	if (node.getLabel() != null) // continue etiquetado
    		return true;
    	boolean hayIf = false;
        ASTNode actual = node.getParent();
        while (actual != null 
                && !(actual instanceof WhileStatement) 
                && !(actual instanceof DoStatement) 
                && !(actual instanceof SwitchStatement)) {
        	if (actual instanceof IfStatement)
        		hayIf = true;
            actual = actual.getParent();
        }
        
        if (actual == null || actual instanceof SwitchStatement || actual instanceof EnhancedForStatement) {
        	//System.out.println("Continue de switch");
            return true; 
        }

        if (!hayIf) {
        	//System.out.println("No depende de un if");
        	return true;
        }
        ASTNode loopParent = actual;
        String name;
        Set<String> names;
        if (node.getProperty("continueInsideFinally") != null) {
        	//System.out.println("------------------- Es un continue de finally ---------------\n");
        	//System.out.println(node.getParent());
        	
            names = (Set<String>) loopParent.getProperty("continueNamesFinally");
            name = createName("keep");            
            if (names == null) {
            	names = new HashSet<String>();
            }
            names.add(name);
            loopParent.setProperty("continueNamesFinally", names);
            
            // marcar el break y las sentencias posteriores
            node.setProperty("continueNameFinally", name);
            markFollowingStatements(node, name);            
        	//System.out.println("------------------- Es un continue de finally ANOTADO ---------------\n");
        }
        else {
        	//System.out.println("------------------- Es un continue normal ---------------\n");
        	//System.out.println(node.getParent());
        	
	        name = (String) loopParent.getProperty("continueName");
	        if (name == null) {
	            //name = createName("continue");
	            name = createName("keep");
	            loopParent.setProperty("continueName", name);
	        }
	
	        // marcar el continue y las sentencias posteriores
	        node.setProperty("continueName", name);
            markFollowingStatements(node, name);        
	        //System.out.println("------------------- Es un continue normal ANOTADO ---------------\n");
        }
        return true; // se sigue recorriendo
    }
    
    
    private String createName(String base) {
        int i = 1;
        String candidate;
        do {
            candidate = base + i++;
        } while (usedNames.contains(candidate) || existingNames.contains(candidate));
        usedNames.add(candidate);
        return candidate;
    }
    
    @SuppressWarnings("unchecked")
    private void markFollowingStatements(Statement ruptureStmt, String name) {
        ASTNode parent = ruptureStmt.getParent();
        ASTNode son = ruptureStmt;
        boolean end = false;
        Set<String> moves;
        
        while (parent != null && !end) {
        	
        	end =     (parent instanceof WhileStatement 
                    || parent instanceof DoStatement 
                    || parent instanceof ForStatement 
                    || parent instanceof EnhancedForStatement);

        	if (!end) {
                if (parent instanceof Block block) {
                    List<Statement> stmts = (List<Statement>) block.statements();
                    int index = stmts.indexOf(son);
                    
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
