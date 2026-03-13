package org.plugin.refactor.code;


import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.PlatformUI;

/**
 * Eclipse command handler that orchestrates the complete
 * control-flow refactoring pipeline.
 *
 * <p>This handler executes a multi-phase transformation over the
 * active Java compilation unit. The process includes:</p>
 *
 * <ol>
 *   <li>Parsing the source into an AST.</li>
 *   <li>Transforming {@code for} and enhanced {@code for} loops
 *       into {@code while} loops.</li>
 *   <li>Annotating control-flow rupture statements
 *       ({@code break}/{@code continue}).</li>
 *   <li>Structurally grouping affected statements into nested
 *       {@code if} blocks.</li>
 *   <li>Materializing the final transformation by introducing
 *       boolean control flags and rewriting loop conditions.</li>
 * </ol>
 *
 * <p>Each phase may reparse the compilation unit to ensure that
 * subsequent transformations operate on an updated AST.</p>
 * 
 */
public class RefactorHandler extends AbstractHandler {

	/**
     * Executes the refactoring process on the currently active
     * Java editor.
     *
     * <p>The transformation is applied in multiple sequential phases,
     * each updating the source buffer and reparsing the AST when
     * necessary.</p>
	 * 
     * @param event the execution event triggered by Eclipse
     * @return null
     * @throws ExecutionException if execution fails
	 * 
	 */
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
           IEditorPart editor = PlatformUI.getWorkbench()
                   .getActiveWorkbenchWindow()
                   .getActivePage()
                   .getActiveEditor();

           if (!(editor instanceof ITextEditor)) {
               return null;
           }

           ICompilationUnit unit = (ICompilationUnit) JavaUI.getEditorInputJavaElement(editor.getEditorInput());

           // =========================
           // Phase 0: Parser the AST
           // =========================
           ASTParser parser = ASTParser.newParser(AST.JLS21);
           parser.setKind(ASTParser.K_COMPILATION_UNIT);
           parser.setSource(unit);
           parser.setResolveBindings(true);
           parser.setBindingsRecovery(true); // bindings
           parser.setProject(unit.getJavaProject());

           CompilationUnit cu = (CompilationUnit) parser.createAST(null);
           Document document = new Document(unit.getSource());
            
           // ===========================
           // Phase 1a: For → While
           // ===========================
           ForMarkerVisitor forMarker = new ForMarkerVisitor();
           cu.accept(forMarker);

           if (!forMarker.getMarkedFors().isEmpty()) {
               ASTRewrite forRewrite = ASTRewrite.create(cu.getAST());
               ForToWhileVisitor forConverter = new ForToWhileVisitor(forRewrite);
               cu.accept(forConverter);

               document = new Document(unit.getSource()); 
               TextEdit edits = forRewrite.rewriteAST(document, null);
               edits.apply(document);

               unit.getBuffer().setContents(document.get());
               unit.save(null, true);

               // AST update
               parser.setSource(unit);
               cu = (CompilationUnit) parser.createAST(null);
           }

           // =======================================
           // Phase 1b: ForEach → While with Iterator
           // =======================================
           ForEachMarkerVisitor foreachMarker = new ForEachMarkerVisitor();
           cu.accept(foreachMarker);

           if (!foreachMarker.getMarkedFors().isEmpty()) {
               ASTRewrite foreachRewrite = ASTRewrite.create(cu.getAST());
               ForEachToWhileVisitor foreachConverter =
                       new ForEachToWhileVisitor(foreachMarker.getMarkedFors(), foreachRewrite, cu);
               cu.accept(foreachConverter);

               document = new Document(unit.getSource());
               TextEdit edits = foreachRewrite.rewriteAST(document, null);
               edits.apply(document);

               unit.getBuffer().setContents(document.get());
               unit.save(null, true);

               parser.setSource(unit);
               cu = (CompilationUnit) parser.createAST(null);
           }

           // =======================================
           // Fase 2: Analysis: Mark breaks and continues
           // =======================================
            
           // Finally breaks or continues 
           ExceptionAnnotator exceptionAnnotator = new ExceptionAnnotator(cu);
           cu.accept(exceptionAnnotator);
           // normal breaks and continues
           cu.accept(new FlowAnnotator(cu));
           ASTRewrite structuralRewrite = ASTRewrite.create(cu.getAST());

           // =======================================
           // Phase 3: if blocks
           // =======================================
            
           cu.accept(new LoopRewriterFirst(structuralRewrite));
           document = new Document(unit.getSource());
           TextEdit edits = structuralRewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));
           edits.apply(document);

           unit.getBuffer().setContents(document.get());
           unit.save(null, true);

           // =======================================
           // Phase 4: Reanalysis
           // =======================================
            
           parser.setSource(unit);
           cu = (CompilationUnit) parser.createAST(null);
            
           cu.accept(new ExceptionAnnotator(cu));
           cu.accept(new FlowAnnotator(cu));

           // =======================================
           // Phase 5: final writing
           // =======================================
           /*
            * Declare booleans,
			* change breaks and continues to stayX = false and keepY = false
			* Add conditions to loops            
            */
           ASTRewrite finalFlowRewrite = ASTRewrite.create(cu.getAST());
           cu.accept(new LoopRewriterFinal(finalFlowRewrite));
            
           // Apply changes ---
           document = new Document(unit.getSource());
           TextEdit edits2 = finalFlowRewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));
           edits2.apply(document);

           // Save changes to the original file
           unit.getBuffer().setContents(document.get());
           unit.save(null, true);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
