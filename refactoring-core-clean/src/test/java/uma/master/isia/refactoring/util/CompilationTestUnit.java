package uma.master.isia.refactoring.util;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class CompilationTestUnit {

	public static CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());

		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);

		parser.setUnitName("A.java"); // obligatorio

		String jrePath = System.getProperty("java.home");

		String[] classpath = { jrePath };  // JDK 9+ usa módulos (jrt)

		parser.setEnvironment(classpath, null, null, true);
		
		return (CompilationUnit) parser.createAST(null);

		/*
	    ASTParser parser = ASTParser.newParser(AST.JLS21); // Ajusta a JLS8 si prefieres
	    parser.setSource(source.toCharArray());
	    parser.setKind(ASTParser.K_COMPILATION_UNIT);
	    
	    // Configuración para que el Test 2 (IProblem) funcione:
	    parser.setResolveBindings(true);
	    parser.setBindingsRecovery(true);
	    parser.setStatementsRecovery(true);
	    parser.setUnitName("Test.java");
	    
	    // Necesitamos darle el classpath del sistema para los problemas de compilación
	    parser.setEnvironment(null, null, null, true); 
	    
	    return (CompilationUnit) parser.createAST(null);
	    */
	}
	
}
