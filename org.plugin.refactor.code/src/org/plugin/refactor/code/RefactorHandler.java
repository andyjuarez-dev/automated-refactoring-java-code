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

public class RefactorHandler extends AbstractHandler {

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
            // Fase 0: Parseamos el AST
            // =========================
            ASTParser parser = ASTParser.newParser(AST.JLS21);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(unit);
            parser.setResolveBindings(true);
            parser.setBindingsRecovery(true); // Ayuda si faltan dependencias
            parser.setProject(unit.getJavaProject());

            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            Document document = new Document(unit.getSource());
            
            // ===========================
            // Fase 1a: For → While
            // ===========================
            ForMarkerVisitor forMarker = new ForMarkerVisitor();
            cu.accept(forMarker);

            // Si hay ciclos for
            if (!forMarker.getMarkedFors().isEmpty()) {
            	
            	// transforma a ciclos while
                ASTRewrite forRewrite = ASTRewrite.create(cu.getAST());
                //ForToWhileVisitor4 forConverter = new ForToWhileVisitor4(forMarker.getMarkedFors(), forRewrite);
                ForToWhileVisitor forConverter = new ForToWhileVisitor(forRewrite);
                cu.accept(forConverter);

                // guarda el documento
                document = new Document(unit.getSource()); 
                TextEdit edits = forRewrite.rewriteAST(document, null);
                edits.apply(document);

                unit.getBuffer().setContents(document.get());
                unit.save(null, true);

                // volver a parsear el AST para que se actualice
                parser.setSource(unit);
                cu = (CompilationUnit) parser.createAST(null);
            }

            // =======================================
            // Fase 1b: ForEach → While con Iterator
            // =======================================
            ForEachMarkerVisitor foreachMarker = new ForEachMarkerVisitor();
            cu.accept(foreachMarker);

            // Si hay ciclos foreach
            if (!foreachMarker.getMarkedFors().isEmpty()) {
                ASTRewrite foreachRewrite = ASTRewrite.create(cu.getAST());
                ForEachToWhileVisitor foreachConverter =
                        new ForEachToWhileVisitor(foreachMarker.getMarkedFors(), foreachRewrite, cu);
                cu.accept(foreachConverter);

                // Actualiza el documento
                document = new Document(unit.getSource());
                TextEdit edits = foreachRewrite.rewriteAST(document, null);
                edits.apply(document);

                unit.getBuffer().setContents(document.get());
                unit.save(null, true);

                // volver a parsear el AST para que la siguiente etapa lo tome con los cambios
                parser.setSource(unit);
                cu = (CompilationUnit) parser.createAST(null);
            }

            // =======================================
            // Fase 2: Marcado de breaks y continues
            // =======================================
            
            // Marca las excepciones, si hay breaks o continues dentro de un finally
            ExceptionAnnotator annotator = new ExceptionAnnotator(cu);
            cu.accept(annotator);
            // Marca el resto de breaks y continues
            cu.accept(new FlowAnnotator(cu));
            ASTRewrite rewriter = ASTRewrite.create(cu.getAST());

            // =======================================
            // Fase 3: Arma bloques if
            // =======================================
            
            // Crea la estructura ANIDADA de IFs (preservando el scope)
            cu.accept(new LoopRewriterFirst(rewriter));
            document = new Document(unit.getSource());
            TextEdit edits = rewriter.rewriteAST(document, unit.getJavaProject().getOptions(true));
            edits.apply(document);

            // Guardar cambios en el archivo original
            unit.getBuffer().setContents(document.get());
            unit.save(null, true);

            // =======================================
            // Fase 4: Reanalisis
            // =======================================
            
            // volver a parsear el AST para que la siguiente etapa lo tome con los cambios
            parser.setSource(unit);
            cu = (CompilationUnit) parser.createAST(null);
            
            cu.accept(new ExceptionAnnotator(cu));
            cu.accept(new FlowAnnotator(cu));

            // =======================================
            // Fase 5: Escritura final
            // =======================================
            // Declara booleanas, 
            // cambia break y continue por stayX = false y keepY = false
            // Agrega condiciones a los ciclos
            ASTRewrite rewriter2 = ASTRewrite.create(cu.getAST());
            cu.accept(new LoopRewriterFinal(rewriter2));
            
            // Aplicar Cambios ---
            document = new Document(unit.getSource());
            TextEdit edits2 = rewriter2.rewriteAST(document, unit.getJavaProject().getOptions(true));
            edits2.apply(document);

            // Guardar cambios en el archivo original
            unit.getBuffer().setContents(document.get());
            unit.save(null, true);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
