package uma.master.isia.refactoring.util;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;

public class StructureVisitor extends ASTVisitor {

	int forCount = 0;
    int whileCount = 0;
    int doCount = 0;
    int enhancedForCount = 0;
    int booleanFlagCount = 0;
    boolean hasBooleanFlag = false;
    boolean whileHasAndCondition = false;
    int forMarkedCount = 0;
    int enhancedForMarkedCount = 0;
    int breakCount = 0;
    int continueCount = 0;
    int doMarkedCount = 0;

    @Override
    public boolean visit(ForStatement node) {
        forCount++;
        if (node.getProperty("change") != null)
        	forMarkedCount++;
        return true;
    }

    @Override
    public boolean visit(DoStatement node) {
        doCount++;
        if (node.getProperty("change") != null)
        	doMarkedCount++;
        return true;
    }
    
    @Override
    public boolean visit(BreakStatement node) {
        breakCount++;
        return true;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        continueCount++;
        return true;
    }
    
    
    @Override
    public boolean visit(EnhancedForStatement node) {
        enhancedForCount++;
        if (node.getProperty("change") != null)
        	enhancedForMarkedCount++;
        return true;
    }
    
    @Override
    public boolean visit(WhileStatement node) {
        whileCount++;

        Expression expr = node.getExpression();
        if (expr instanceof InfixExpression inf &&
            inf.getOperator() == InfixExpression.Operator.CONDITIONAL_AND) {
            whileHasAndCondition = true;
        }

        return true;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        if (node.getType().toString().equals("boolean")) {
            hasBooleanFlag = true;
            booleanFlagCount++;
        }
        return true;
    }
    
    public int getForCount() {
    	return forCount;
    }

    public int getWhileCount() {
    	return whileCount;
    }
    
    public boolean getHasBooleanFlag() {
    	return hasBooleanFlag;
    }

    public boolean getWhileHasAndCondition() {
    	return whileHasAndCondition;
    }

    public int getBooleanFlag() {
    	return booleanFlagCount;
    }

    public int getForMarkedCount() {
    	return forMarkedCount;
    }

    public int getEnhancedForCount() {
    	return enhancedForCount;
    }

    public int getBreakCount() {
    	return breakCount;
    }
 
    public int getDoCount() {
    	return doCount;
    }

    public int getContinueCount() {
    	return continueCount;
    }
    
}
