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

public class LoopRewriterFinal extends ASTVisitor {


    private ASTRewrite rewrite;

    public LoopRewriterFinal(ASTRewrite rewrite) {
        this.rewrite = rewrite;
    }
    
    @Override
    public void endVisit(BreakStatement node) {
      	// Reemplaza el continue por la variable que sea = false
          String nombre = (String) node.getProperty("breakName");
//          System.out.println("entro en el continue");
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

  @Override
  public void endVisit(ContinueStatement node) {
    	// Reemplaza el continue por la variable que sea = false
        String nombre = (String) node.getProperty("continueName");
//        System.out.println("entro en el continue");
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
    

  @Override
  public boolean visit(WhileStatement node) {
	  if (needsFlowRewrite(node))
		  processLoop3(node);
      return true;
  }

  
  @Override
  public boolean visit(DoStatement node) {
	  if (needsFlowRewrite(node))
		  processLoop3(node);
      return true;
  }

  private boolean needsFlowRewrite(Statement node) {
	    return node.getProperty("breakName") != null
	        || node.getProperty("breakNamesFinally") != null
	        || node.getProperty("continueName") != null
	        || node.getProperty("continueNamesFinally") != null;
	}
  
  @SuppressWarnings("unchecked")
private void processLoop3(Statement loopNode) {

  	/*
  	 * Esta parte agrega las booleanas necesarias (antes o despues del while, por ahora, solo antes)
  	 */
      String nameB  = (String) loopNode.getProperty("breakName");
      Set<String> namesF = (Set<String>) loopNode.getProperty("breakNamesFinally");
      String continueName = (String) loopNode.getProperty("continueName");
      Set<String> contNamesF = (Set<String>) loopNode.getProperty("continueNamesFinally");
      
      if ((nameB == null) && (namesF == null) && (continueName == null) && (contNamesF == null)) 
      	return;

      AST ast = loopNode.getAST();

      if (nameB != null) {
          // crear: boolean nombre = true;
          VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
          frag.setName(ast.newSimpleName(nameB));
          frag.setInitializer(ast.newBooleanLiteral(true));
          VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
          decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

          // insertar declaracion antes del loop o envolver en Block si el parent no es Block
          insertDeclarationBefore(loopNode, decl);
      }

      if (namesF != null) {
          // crear: boolean nombre = true;
          //VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
          for (String name : namesF) {
              VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
              frag.setName(ast.newSimpleName(name));
              frag.setInitializer(ast.newBooleanLiteral(true));
              VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
              decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

              // insertar declaracion antes del loop o envolver en Block si el parent no es Block
              insertDeclarationBefore(loopNode, decl);
          }
      }

      /*
       * Esta parte cambia las condiciones del while
       */
      if ((nameB != null) || (namesF != null)) {
	        Expression originalCond = null;
	        if (loopNode instanceof WhileStatement) {
	            originalCond = ((WhileStatement) loopNode).getExpression();
	        } else if (loopNode instanceof DoStatement) {
	            originalCond = ((DoStatement) loopNode).getExpression();
	        }
	        
	        ParenthesizedExpression paren = ast.newParenthesizedExpression();
	        // usar createCopyTarget para que ASTRewrite maneje la copia
	        paren.setExpression((Expression) rewrite.createCopyTarget(originalCond));
	        Expression ed = paren;
	        InfixExpression newCond = null;
	
	        List<String> nombres = new ArrayList<String>();
	        if (nameB != null) {
	        	nombres.add(nameB);
	        }
	        if (namesF != null) {
	        	for (String s: namesF) {
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
      
      // Parte del CONTINUE ---------------------------------------------
      if (continueName != null) {
          // crear: boolean nombre = true;
          VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
          frag.setName(ast.newSimpleName(continueName));
          frag.setInitializer(ast.newBooleanLiteral(true));
          VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
          decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

          // insertar declaracion despues del loop 
          insertDeclarationFirst(loopNode, decl);
      }

      if (contNamesF != null) {
          // crear: boolean nombre = true;
          //VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
          for (String name : contNamesF) {
              VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
              frag.setName(ast.newSimpleName(name));
              frag.setInitializer(ast.newBooleanLiteral(true));
              VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(frag);
              decl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

              // insertar declaracion antes del loop o envolver en Block si el parent no es Block
              insertDeclarationFirst(loopNode, decl);
          }
      }
  }

  private void insertDeclarationFirst(Statement loopNode, VariableDeclarationStatement decl) {
      ASTNode body = getBody(loopNode);

      if (body != null) {
          // Caso 1: el cuerpo ya es un bloque { ... }
          ListRewrite lr = rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY);
          lr.insertFirst(decl, null);
      } 
  }

  @SuppressWarnings("unchecked")
private void insertDeclarationBefore(Statement loopNode, VariableDeclarationStatement decl) {
	    AST ast = loopNode.getAST();
	    ASTNode parent = loopNode.getParent();

	    if (parent instanceof Block) {
	        ListRewrite listRewrite = rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
	        listRewrite.insertBefore(decl, loopNode, null);
	    } else {
	        // Creamos el bloque que envolverá al if sin llaves
	        Block newBlock = ast.newBlock();
	        
	        // 1. Agregamos la declaración directamente
	        newBlock.statements().add(decl); 
	        
	        // 2. IMPORTANTE: Usamos move target para no perder el rastro del loop
	        // Esto permite que el rewriter sepa que el loopNode "original" 
	        // ahora vive dentro de este bloque.
	        Statement moveTarget = (Statement) rewrite.createMoveTarget(loopNode);
	        newBlock.statements().add(moveTarget);
	        
	        // 3. Reemplazamos el bucle original (que estaba bajo el 'if') por el nuevo bloque
	        rewrite.replace(loopNode, newBlock, null);
	    }
	}  
  
  
  public Block getBody(Statement node) {
  	Block block = null;
  	if (node instanceof WhileStatement ws && ws.getBody() instanceof Block)
  		block = (Block) ws.getBody();
  	if (node instanceof DoStatement ds && ds.getBody() instanceof Block)
  		block = (Block) ds.getBody();

  	return block;
  }
  
}
