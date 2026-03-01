package uma.master.isia.refactoring;

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

public class ExceptionAnnotator extends ASTVisitor {

	private final Set<String> existingNames = new HashSet<>(); // variables/parametros ya declarados
    private boolean breakInsideFinally = false;

    public ExceptionAnnotator(CompilationUnit cu) {
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

    
    @Override
     public boolean visit(TryStatement node) {
        // buscar el while o do-while padre mas cercano
//    	System.out.println("Try: ---------------\n" + node);
    	Block bFin = node.getFinally();
    	if (bFin != null) {
//    		System.out.println("Tiene un finally");
    	    bFin.accept(new ASTVisitor() {
    	        @Override
    	        public boolean visit(BreakStatement breakStmt) {
//    	            System.out.println("Encontrado break en finally: " + breakStmt);
    	        	if (breakStmt.getLabel() != null) // break etiquetado
    	        		return true;

    	            breakStmt.setProperty("breakInsideFinally", true);
    	            
    	            breakInsideFinally = true;
    	            return super.visit(breakStmt);
    	        }
    	        
    	        @Override
    	        public boolean visit(ContinueStatement continueStmt) {
//    	            System.out.println("Encontrado continue en finally: " + continueStmt);
    	        	if (continueStmt.getLabel() != null) // continue etiquetado
    	        		return true;

    	            continueStmt.setProperty("continueInsideFinally", true);
    	        	return super.visit(continueStmt);
    	        }
    	    });

    	}
        return true;
    }
    
    public boolean corteInsideFinally() {
    	return breakInsideFinally;
    }
    
}
