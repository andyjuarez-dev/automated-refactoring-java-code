package uma.master.isia.refactoring;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import uma.master.isia.refactoring.util.CompilationTestUnit;
import uma.master.isia.refactoring.util.StructureVisitor;

public class ForMarkerVisitorTest {

	@Test
	void testStructural_ForWithBreak() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					for (int i = 0; i < 10; i++) {
						if (i == 5)
							break;
						System.out.println(i);
					}
				}
	        }
	        """;

	    String result = RefactoringEngine.runFirstPartPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getForCount(), "No deberia haber for"),
	        () -> assertEquals(1, v.getWhileCount(), "Deberia haber un while")
	    );
	}

	@Test
	void testStructural_ForWithContinue() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					for (int i = 0; i < 10; i++) {
						if (i == 5)
							continue;
						System.out.println(i);
					}
				}
	        }
	        """;

	    String result = RefactoringEngine.runFirstPartPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
		        () -> assertEquals(0, v.getForCount(), "No deberia haber for"),
		        () -> assertEquals(1, v.getWhileCount(), "Deberia haber un while")
	    );
	}

	@Test
	void testStructural_ForWithLabelContinue() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					outer:
					for (int i = 0; i < 10; i++) {
						for (int j = 0; j < 10; j++) {
							if (j == 5)
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
	        () -> assertEquals(2, v.getForCount(), "Deberia haber 2 for"),
	        () -> assertEquals(0, v.getForMarkedCount(), "No deberia haber marcado ningun for")
	    );
	}
	
	
	@Test
	void testStructural_ForWithLabelBreak() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					outer:
					for (int i = 0; i < 10; i++) {
						for (int j = 0; j < 10; j++) {
							if (j == 5)
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
	        () -> assertEquals(2, v.getForCount(), "Deberia haber 2 for"),
	        () -> assertEquals(0, v.getForMarkedCount(), "No deberia haber marcado ningun for")
	    );
	}
	
}
