package uma.master.isia.refactoring;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;


public class RefactoringEngine {

	public static String runFullPipeline(String source) throws Exception {
	    String step1 = applyForToWhile(source);
	    String step2 = applyForEachToWhile(step1);
	    String step3 = applyStructuralRefactoring1(step2); // Aquí irían FlowAnnotator y LoopRewriter
	    String step4 = applyStructuralRefactoring2(step3);
	    return step4;
	}
	
	public static String runFirstPartPipeline(String source) throws Exception {
	    String step1 = applyForToWhile(source);
	    String step2 = applyForEachToWhile(step1);
	    //String step3 = applyStructuralRefactoring1(step2); // Aquí irían FlowAnnotator y LoopRewriter
	    //String step4 = applyStructuralRefactoring2(step3);
	    return step2;
	}
	
	
	private static String applyForToWhile(String source) throws Exception {
	    // 1. Configurar el Parser para trabajar con Strings
	    ASTParser parser = ASTParser.newParser(AST.JLS21);
	    parser.setSource(source.toCharArray());
	    parser.setKind(ASTParser.K_COMPILATION_UNIT);
	    parser.setResolveBindings(true);
	    parser.setEnvironment(null, null, null, true); // Usa el classpath del sistema
	    parser.setUnitName("Temp.java");

	    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
	    
	    // 2. Ejecutar el Marcador
	    ForMarkerVisitor forMarker = new ForMarkerVisitor();
	    cu.accept(forMarker);

	    // 3. Si hay cambios, aplicar la transformación
	    if (!forMarker.getMarkedFors().isEmpty()) {
	        ASTRewrite forRewrite = ASTRewrite.create(cu.getAST());
	        ForToWhileVisitor forConverter = new ForToWhileVisitor(forRewrite);
	        cu.accept(forConverter);

	        // Aplicar los cambios al documento en memoria
	        IDocument document = new Document(source);
	        TextEdit edits = forRewrite.rewriteAST(document, java.util.Collections.emptyMap());
	        edits.apply(document);
	        
	        return document.get(); // Retornamos el código ya transformado
	    }
	    
	    return source; // Si no hubo cambios, devolvemos el original
	}
	
	
	private static String applyForEachToWhile(String source) throws Exception {
	    ASTParser parser = createParser(source);
	    CompilationUnit cu = (CompilationUnit) parser.createAST(null);

	    ForEachMarkerVisitor foreachMarker = new ForEachMarkerVisitor();
	    cu.accept(foreachMarker);

	    if (!foreachMarker.getMarkedFors().isEmpty()) {
	        ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
	        // Pasamos 'cu' porque tu constructor de ForEachToWhileVisitor3 lo pide
	        ForEachToWhileVisitor converter = new ForEachToWhileVisitor(foreachMarker.getMarkedFors(), rewrite, cu);
	        cu.accept(converter);

	        return executeRewrite(source, rewrite);
	    }
	    return source;
	}	
	
	private static String applyStructuralRefactoring1(String source) throws Exception {
	    ASTParser parser = createParser(source);
	    CompilationUnit cu = (CompilationUnit) parser.createAST(null);

	    // Fase 1: Anotación de flujo (FlowAnnotator)
	    cu.accept(new ExceptionAnnotator(cu));
	    cu.accept(new FlowAnnotator(cu));

	    // Fase 2: Reescritura de bucles (LoopRewriter)
	    ASTRewrite rewriter = ASTRewrite.create(cu.getAST());
	    cu.accept(new LoopRewriterFirst(rewriter));

	    return executeRewrite(source, rewriter);
	}

	private static String applyStructuralRefactoring2(String source) throws Exception {
	    ASTParser parser = createParser(source);
	    CompilationUnit cu = (CompilationUnit) parser.createAST(null);

	    // Fase 1: Anotación de flujo (FlowAnnotator)
	    cu.accept(new ExceptionAnnotator(cu));
	    cu.accept(new FlowAnnotator(cu));

	    // Fase 2: Reescritura de bucles (LoopRewriter)
	    ASTRewrite rewriter = ASTRewrite.create(cu.getAST());
	    cu.accept(new LoopRewriterFinal(rewriter));

	    return executeRewrite(source, rewriter);
	}
	
	
	// Helpers
	private static ASTParser createParser(String source) {
	    ASTParser parser = ASTParser.newParser(AST.JLS21);
	    parser.setSource(source.toCharArray());
	    parser.setKind(ASTParser.K_COMPILATION_UNIT);
	    parser.setResolveBindings(true);
	    parser.setEnvironment(null, null, null, true);
	    parser.setUnitName("Temp.java");
	    return parser;
	}

	private static String executeRewrite(String source, ASTRewrite rewriter) throws Exception {
	    IDocument document = new Document(source);
	    TextEdit edits = rewriter.rewriteAST(document, java.util.Collections.emptyMap());
	    edits.apply(document);
	    return document.get();
	}	
}
