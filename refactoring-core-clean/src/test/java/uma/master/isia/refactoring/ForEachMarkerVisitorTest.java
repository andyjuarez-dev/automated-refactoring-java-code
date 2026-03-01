package uma.master.isia.refactoring;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import uma.master.isia.refactoring.util.CompilationTestUnit;
import uma.master.isia.refactoring.util.StructureVisitor;

public class ForEachMarkerVisitorTest {


	@Test
	void testStructural_ForEachWithBreakWithIterator() throws Exception {

	    String input = """
		    	import java.util.List;
		    	
		        public class A {
					public void m() {
						List<String> nombres = new ArrayList<>();
						nombres.add("Juan");
						nombres.add("Silvia");
						nombres.add("Ramon");
						nombres.add("Laura");
						
						for (String s: nombres) {
							System.out.println(s);
							if (s.equalsIgnoreCase("Ramon"))
								break;
						}
					}
		        }
		        """;

		    String result = RefactoringEngine.runFullPipeline(input);
		    CompilationUnit cu = CompilationTestUnit.parse(result);
		    StructureVisitor v = new StructureVisitor();
		    cu.accept(v);

		    assertAll("Estructura del AST",
			        () -> assertEquals(0, v.getEnhancedForCount(), "Todavía hay un foreach"),
			        () -> assertEquals(1, v.getWhileCount(), "No se generó while"),
			        () -> assertTrue(v.getHasBooleanFlag(), "No se declaró la variable booleana")
			    );
	}

	@Test
	void testStructural_ForEachWithContinueAndIterator() throws Exception {

	    String input = """
		    	import java.util.List;
		    	
		        public class A {
					public void m() {
						List<String> nombres = new ArrayList<>();
						nombres.add("Juan");
						nombres.add("Silvia");
						nombres.add("Ramon");
						nombres.add("Laura");
						
						for (String s: nombres) {
							System.out.println(s);
							if (s.equalsIgnoreCase("Ramon"))
								continue;
						}
					}
		        }
		        """;

		    String result = RefactoringEngine.runFullPipeline(input);
		    CompilationUnit cu = CompilationTestUnit.parse(result);
		    StructureVisitor v = new StructureVisitor();
		    cu.accept(v);

		    assertAll("Estructura del AST",
			        () -> assertEquals(0, v.getEnhancedForCount(), "Todavía hay un foreach"),
			        () -> assertEquals(1, v.getWhileCount(), "No se generó while"),
			        () -> assertTrue(v.getHasBooleanFlag(), "No se declaró la variable booleana")
			    );
	}

	@Test
	void testStructural_ForEachWithLabelContinue() throws Exception {

	    String input = """
		    	import java.util.List;
		    	
		        public class A {
					public void m() {
						List<String> nombres = new ArrayList<>();
						nombres.add("Juan");
						nombres.add("Silvia");
						nombres.add("Ramon");
						nombres.add("Laura");
						outer:
						for (int i = 0; i < 10; i++) {
							for (String s: nombres) {
								System.out.println(s);
								if (s.equalsIgnoreCase("Ramon"))
									continue outer;
							}
						}
					}
		        }
		        """;

		    String result = RefactoringEngine.runFullPipeline(input);
		    CompilationUnit cu = CompilationTestUnit.parse(result);
		    StructureVisitor v = new StructureVisitor();
		    cu.accept(v);

		    assertAll("Estructura del AST",
		        () -> assertEquals(1, v.getEnhancedForCount(), "Debería haber un foreach"),
		        () -> assertEquals(0, v.getWhileCount(), "No debería haber while")
		    );
	}
	
	
	@Test
	void testStructural_ForEachWithLabelBreak() throws Exception {

	    String input = """
		    	import java.util.List;
		    	
		        public class A {
					public void m() {
						List<String> nombres = new ArrayList<>();
						nombres.add("Juan");
						nombres.add("Silvia");
						nombres.add("Ramon");
						nombres.add("Laura");
						outer:
						for (int i = 0; i < 10; i++) {
							for (String s: nombres) {
								System.out.println(s);
								if (s.equalsIgnoreCase("Ramon"))
									break outer;
							}
						}
					}
		        }
		        """;

	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
		        () -> assertEquals(1, v.getEnhancedForCount(), "Debería haber un foreach"),
		        () -> assertEquals(0, v.getWhileCount(), "No debería haber while")
		    );
	}
}
