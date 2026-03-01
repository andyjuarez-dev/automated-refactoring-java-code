package uma.master.isia.refactoring;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import uma.master.isia.refactoring.util.CompilationTestUnit;
import uma.master.isia.refactoring.util.StructureVisitor;

public class ForEachToWhileVisitorTest {

	@Test
	void testStructural_ForEachWithUnknowClass() throws Exception {

	    String input = """
		    	import java.util.List;
		    	
		        public class A {
					public void m() {
						List<AnyClass> lista = new ArrayList<>();
						lista.add(new AnyClass());
						lista.add(new AnyClass());
						int x = 0;
						for (AnyClass ac: lista) {
							System.out.println(ac);
							if (x > 2)
								break;
							x++;
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
			        () -> assertEquals(0, v.getWhileCount(), "No debería haber ningún while")
			    );
	}
	
	@Test
	void testStructural_ForEachWithoutBreak() throws Exception {

	    String input = """
		        public class A {
					public void m() {
						int numeros[] = {5, 3, 8, 7};
						
						for (Integer x: numeros) {
							System.out.println(x);
						}
					}
		        }
		        """;

		    String result = RefactoringEngine.runFullPipeline(input);
		    CompilationUnit cu = CompilationTestUnit.parse(result);
		    StructureVisitor v = new StructureVisitor();
		    cu.accept(v);

		    assertAll("Estructura del AST",
			        () -> assertEquals(1, v.getEnhancedForCount(), "Debería estar el foreach"),
			        () -> assertEquals(0, v.getWhileCount(), "No debería haber while")
			    );
	}

	
	@Test
	void testStructural_ForEachWithBreakPrimitiveValues() throws Exception {

	    String input = """
		        public class A {
					public void m() {
						int numeros[] = {5, 3, 8, 7};
						
						for (Integer x: numeros) {
							System.out.println(x);
							if (x == 3)
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
	void testStructural_ForEachWithBreakWithVector() throws Exception {

	    String input = """
		        public class A {
					public void m() {
						String nombres[] = {\"Juan\", \"Silvia\", \"Ramon\", \"Laura\"};
						
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
