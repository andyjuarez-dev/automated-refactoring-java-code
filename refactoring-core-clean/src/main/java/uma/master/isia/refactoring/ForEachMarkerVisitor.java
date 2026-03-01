package uma.master.isia.refactoring;

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

public class ForEachMarkerVisitor extends ASTVisitor {

    private final Set<EnhancedForStatement> markedFors;
    
    public ForEachMarkerVisitor() {
    	markedFors = new HashSet<>();
    }
    

    @Override
    public boolean visit(BreakStatement node) {
    	if (node.getLabel() != null) // break etiquetado
    		return true;

        markForEachParent(node);
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
    	if (node.getLabel() != null) // continue etiquetado
    		return true;

        markForEachParent(node);
        return super.visit(node);
    }

    /**
     * Busca hacia arriba en el arbol hasta encontrar el EnhancedForStatement padre
     * y lo marca para la conversion.
     */
    private void markForEachParent(Statement stmt) {
        ASTNode parent = stmt.getParent();
        boolean hasIf = false;
        while (parent != null && !(parent instanceof ForStatement)
        		&& !(parent instanceof WhileStatement) && !(parent instanceof DoStatement)
        		&& !(parent instanceof EnhancedForStatement)) {
        	if (parent instanceof IfStatement) 
        		hasIf = true;
        	parent = parent.getParent();
        }
        if (!hasIf) {
//        	System.out.println("Es un break o continue degenerado");
        	return;
        }
        if (parent instanceof EnhancedForStatement efs) {
            markedFors.add(efs);
            efs.setProperty("change", true);
        }
    }

    public Set<EnhancedForStatement> getMarkedFors() {
        return markedFors;
    }
}
