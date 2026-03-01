package org.plugin.refactor.code;

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

public class ForMarkerVisitor extends ASTVisitor {
    private final Set<ForStatement> markedFors; 
    
    public ForMarkerVisitor() { 
    	markedFors = new HashSet<>();
    }

    @Override
    public boolean visit(BreakStatement node) {
    	if (node.getLabel() != null) // break etiquetado
    		return true;

        markForParent(node, "hasBreak");
        return super.visit(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
    	if (node.getLabel() != null) // continue etiquetado
    		return true;

        markForParent(node, "hasContinue");
        return super.visit(node);
    }

    private void markForParent(Statement stmt, String property) {
        ASTNode parent = stmt.getParent();
        boolean hasIf = false;
        while    (parent != null && 
        		!(parent instanceof ForStatement) && 
        		!(parent instanceof EnhancedForStatement) &&
        		!(parent instanceof WhileStatement) && 
        		!(parent instanceof DoStatement)) {
        	if (parent instanceof IfStatement) 
        		hasIf = true;
            parent = parent.getParent();
        }
        if (!hasIf) {
//        	System.out.println("Es un break o continue degenerado");
        	return;
        }
        if (parent instanceof ForStatement fs) {
            markedFors.add(fs);
            fs.setProperty("change", true); 
            fs.setProperty(property, true);
        }
    }

    public Set<ForStatement> getMarkedFors() {
        return markedFors;
    }
}
