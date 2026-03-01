package uma.master.isia.refactoring;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import uma.master.isia.refactoring.util.BehaviorTestHelper;
import uma.master.isia.refactoring.util.CompilationTestHelper;
import uma.master.isia.refactoring.util.CompilationTestUnit;
import uma.master.isia.refactoring.util.StructureVisitor;

public class StructuralTest {
	
	/*
	 * Test Código defensivo
	 */
	@Test
	void testInvalidCodeDoesNotCrashBreak() {
	    String broken = "class A { void m(){ break; } }";
	    assertDoesNotThrow(() -> RefactoringEngine.runFullPipeline(broken));
	}

	@Test
	void testInvalidCodeDoesNotCrashContinue() {
	    String broken = "class A { void m(){ continue; } }";
	    assertDoesNotThrow(() -> RefactoringEngine.runFullPipeline(broken));
	}

	
	/*
	 * Tests semánticos ----------------------------------------------------------------------------------------
	 */
	
	
	
	@Test
	void testBehaviorPreserved_ForWithBreak() throws Exception {

	    String input = """
		        public class A {
					public void m() {
						int i = 0;
						while (i < 10) {
							if (i == 3) {
								i += 2;
								System.out.println("hola");
								break;
							}
							i++;
						}
						
						for (i = 0; i < 10; i++) {
							if (i > 3) {
								if (i % 2 == 0) {
									System.out.println("anidado");
								}
							}
							System.out.println("algo");
						}
				
						i = 0;
						while (i < 10) {
							if (i == 5) {
								i += 2;
								System.out.println("otro while con if");
								break;
							}
							i++;
						}
				
						for (i = 0; i < 10; i++) 
							if (i > 3)  break;
						
					}
		        }
		        """;

	    String refactored = RefactoringEngine.runFullPipeline(input);
	    BehaviorTestHelper.assertBehaviorPreserved("A", input, refactored);
	}

	
	@Test
	void testBehaviorPreserved_ForAndWhileWithBreak_ejemplo2() throws Exception {

	    String input = """
	        public class A {
	            void m() {
	                for(int i=0; i<10; i++) {
	                    if(i==5) break;
	                    System.out.println(i);
	                }
	            }
	        }
	        """;

	    String refactored = RefactoringEngine.runFullPipeline(input);
	    BehaviorTestHelper.assertBehaviorPreserved("A", input, refactored);
	}
	
	
	/*
	 * Test de compilación ---------------------------------------------------------------------------
	 */
	@Test
	void testRefactoredCodeCompiles() throws Exception {

	    String input = """
	        public class A {
	            void m() {
	                for(int i=0; i<10; i++) {
	                    if(i==5) break;
	                    System.out.println(i);
	                }
	            }
	        }
	        """;

	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationTestHelper.assertCompiles("A", result);
	}

	
	@Test
	void testRefactoredCodeCompiles_ejemplo2() throws Exception {

	    String input = """
		        public class A {
					public void m() {
						int i = 0;
						while (i < 10) {
							if (i == 3) {
								i += 2;
								System.out.println("hola");
								break;
							}
							i++;
						}
						
						for (i = 0; i < 10; i++) {
							if (i > 3) {
								if (i % 2 == 0) {
									System.out.println("anidado");
								}
							}
							System.out.println("algo");
						}
				
						i = 0;
						while (i < 10) {
							if (i == 5) {
								i += 2;
								System.out.println("otro while con if");
								break;
							}
							i++;
						}
				
						for (i = 0; i < 10; i++) 
							if (i > 3)  break;
						
					}
		        }
		        """;

	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationTestHelper.assertCompiles("A", result);
	}
	
	
	/*
	 * Tests estructurales -----------------------------------------------------------------------------------
	 */

	@Test
	void testForContinueOfSwitch() throws Exception {

	    String input = """
        public class A {
			void m() {
				for (int i = 0; i < 5; i++) {
					System.out.println(i);
					switch(i) {
					case 0: System.out.println("cero"); continue;
					default: System.out.println("otro valor"); continue;
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(2, v.getContinueCount(), "Deberían haber 2 continues"),
	        () -> assertEquals(1, v.getForCount(), "Debería haber un for"),
	        () -> assertEquals(0, v.getWhileCount(), "No deberían haber ningún while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No deberían haber booleanas")
	    );
	}

	
	@Test
	void testEnhancedForWithoutBreak() throws Exception {

	    String input = """
        public class A {
			void m() {
	    		  	int x = 5;
					int [] vector = new int[5];
					vector[0] = 8;
					vector[1] = 8;
					vector[2] = 8;
					vector[3] = 8;
					vector[4] = 8;
					
					// CASO 1: array primitivo
					if (x == 5)
						for (int x : vector) {
							System.out.println(x);
							if (x == 5)
								System.out.println("hola");
						}
	        }
	    }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getEnhancedForCount(), "Debería haber un foreach"),
	        () -> assertEquals(0, v.getWhileCount(), "No deberían haber ningún while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No deberían haber booleanas")
	    );
	}
	
	@Test
	void testEnhancedForWithoutBlock() throws Exception {

	    String input = """
        public class A {
			void m() {
	    		  	int x = 5;
					int [] vector = new int[5];
					vector[0] = 8;
					vector[1] = 8;
					vector[2] = 8;
					vector[3] = 8;
					vector[4] = 8;
					
					// CASO 1: array primitivo
					if (x == 5)
						for (int x : vector) {
							System.out.println(x);
							if (x == 5)
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(0, v.getEnhancedForCount(), "No debería haber foreach"),
	        () -> assertEquals(1, v.getWhileCount(), "Deberían haber 1 whiles"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Deberían haber 1 booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testStructural_ForEachToWhileWithIterator() throws Exception {

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
	        () -> assertEquals(0, v.getForCount(), "Todavía hay un for"),
	        () -> assertEquals(1, v.getWhileCount(), "No se generó while"),
	        () -> assertTrue(v.getHasBooleanFlag(), "No se declaró la variable booleana"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while no tiene condición con &&")
	    );
	    
	}
	
	@Test
	void testTwoForeachWithBreak_ejemplo18() throws Exception {

	    String input = """
	        public class A {
	    		import java.util.List;
				public void m() {
					int x = 0;
					List<String> nombres1 = new ArrayList<>();
					String nombres2[] = {"Juan", "Silvia", "Ramon", "Laura"};
					
					nombres1.add("Juan");
					nombres1.add("Silvia");
					nombres1.add("Ramon");
					nombres1.add("Laura");
					
					for (String s: nombres1) {
						System.out.println(s);
						if (s.equalsIgnoreCase("Ramon"))
							break;
					}
					
					for (String s: nombres2) {
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(0, v.getEnhancedForCount(), "No debería haber foreach"),
	        () -> assertEquals(2, v.getWhileCount(), "Debería haber dos whiles"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Debería haber dos booleanas")
	    );
	}
	
	@Test
	void testSomeEnhancedFor_ejemplo57() throws Exception {

	    String input = """

	    import java.util.List;
	    		
	    public class A {
	    
	    	class Persona {
				private String nombre;
				private int edad;
				
				public String getNombre() {
					return nombre;
				}
				public void setNombre(String nombre) {
					this.nombre = nombre;
				}
				public int getEdad() {
					return edad;
				}
				public void setEdad(int edad) {
					this.edad = edad;
				}
				public Persona(String nombre, int edad) {
					super();
					this.nombre = nombre;
					this.edad = edad;
				}
	    	}
	    	
			void m() {
					int [] vector = new int[5];
					vector[0] = 8;
					vector[1] = 8;
					vector[2] = 8;
					vector[3] = 8;
					vector[4] = 8;
					
					Persona [] personas = new Persona[4];
					personas[0] = new Persona("Juan", 1);
					personas[1] = new Persona("Juan", 1);
					personas[2] = new Persona("Juan", 1);
					personas[3] = new Persona("Juan", 1);
					
					List<Persona> personas2 = new ArrayList<>();
					personas2.add(new Persona("Maria", 5));
					personas2.add(new Persona("Maria", 5));
					personas2.add(new Persona("Maria", 5));
					
					// CASO 1: array primitivo
					for (int x : vector) {
						System.out.println(x);
						if (x == 5)
							break;
					}
			
					// CASO 2: array de objetos
					for (Persona p: personas) {
						System.out.println(p.getNombre());
						if (p.getEdad() == 5)
							break;
					}
					
					// CASO 3: lista de objetos
					for (Persona p: personas2) {
						System.out.println(p.getNombre());
						if (p.getEdad() == 4)
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(0, v.getEnhancedForCount(), "No debería haber foreach"),
	        () -> assertEquals(3, v.getWhileCount(), "Deberían haber 3 whiles"),
	        () -> assertEquals(3, v.getBooleanFlag(), "Deberían haber 3 booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testNestedExceptionsMixedBreaksAndContinues_ejemplo55() throws Exception {

	    String input = """
	        public class A {
				public boolean [] vec = {false, false, false, false, false, false, false}; // c2, c3... c7
				
				public boolean dev(int pos) {
					return vec[pos - 2];
				}
				
				public void m() {
					int i = 0;
					while (i < 2) {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
						try {
							System.out.println("Sentencia 3");				
							if (dev(2))
								break;
							System.out.println("Sentencia 4");					
						}
						// Vuelve al catch externo
						catch(Exception e) {
							System.out.println("Tome excepcion " + e);	
							System.out.println("Sentencia 5");
							if (dev(3))
								continue;
							System.out.println("Sentencia 6");
						}
						finally {
							System.out.println("Sentencia 7");
							if (dev(4))
								continue;
							System.out.println("Sentencia 8");
							if (dev(5))
								break;
							// Try interno
							try {
								System.out.println("Sentencia 9");				
								if (dev(6))
									continue;
								System.out.println("Sentencia 10");					
							}
							catch(Exception e) {
								System.out.println("Tome excepcion " + e);	
								System.out.println("Sentencia 11");
								if (dev(7))
									continue;
								System.out.println("Sentencia 12");
							}
							finally {
								System.out.println("Sentencia 13");
								if (dev(8))
									break;
								System.out.println("Sentencia 14");					
							}
							System.out.println("Sentencia 15");					
						}
						
						System.out.println("Sentencia 16");	
						System.out.println("Sentencia 18");				
					}
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(7, v.getBooleanFlag(), "Deberían haber 5 booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testNestedExceptionsWithContinues_ejemplo54() throws Exception {

	    String input = """
	        public class A {
				public boolean [] vec = {false, false, false, false, false, false}; // c2, c3... c7
				
				public boolean dev(int pos) {
					return vec[pos - 2];
				}
				
				public void m() {
					int i = 0;
					while (i < 2) {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
						try {
							System.out.println("Sentencia 3");				
							if (dev(2))
								continue;
							System.out.println("Sentencia 4");					
						}
						// Vuelve al catch externo
						catch(Exception e) {
							System.out.println("Tome excepcion " + e);	
							System.out.println("Sentencia 5");
							if (dev(3))
								continue;
							System.out.println("Sentencia 6");
						}
						finally {
							System.out.println("Sentencia 7");
							if (dev(4))
								continue;
							System.out.println("Sentencia 8");
							// Try interno
							try {
								System.out.println("Sentencia 9");				
								if (dev(5))
									continue;
								System.out.println("Sentencia 10");					
							}
							catch(Exception e) {
								System.out.println("Tome excepcion " + e);	
								System.out.println("Sentencia 11");
								if (dev(6))
									continue;
								System.out.println("Sentencia 12");
							}
							finally {
								System.out.println("Sentencia 13");
								if (dev(7))
									continue;
								System.out.println("Sentencia 14");					
							}
							System.out.println("Sentencia 15");					
						}
						
						System.out.println("Sentencia 16");	
						System.out.println("Sentencia 18");				
					}
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(5, v.getBooleanFlag(), "Deberían haber 5 booleanas"),
	        () -> assertFalse(v.getWhileHasAndCondition(), "El while no debería tener AND")
	    );
	}
	
	
	@Test
	void testNestedTryInFinally_ejemplo53() throws Exception {

	    String input = """
	        public class A {
				public boolean [] vec = {false, false, false, false, false, false}; // c2, c3... c7
				
				public boolean dev(int pos) {
					return vec[pos - 2];
				}
				
				public void m() {
					int i = 0;
					while (i < 2) {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
						try {
							System.out.println("Sentencia 3");				
							if (dev(2))
								break;
							System.out.println("Sentencia 4");					
						}
						// Vuelve al catch externo
						catch(Exception e) {
							System.out.println("Tome excepcion " + e);	
							System.out.println("Sentencia 5");
							if (dev(3))
								break;
							System.out.println("Sentencia 6");
						}
						finally {
							System.out.println("Sentencia 7");
							if (dev(4))
								break;
							System.out.println("Sentencia 8");
							// Try interno
							try {
								System.out.println("Sentencia 9");				
								if (dev(5))
									break;
								System.out.println("Sentencia 10");					
							}
							catch(Exception e) {
								System.out.println("Tome excepcion " + e);	
								System.out.println("Sentencia 11");
								if (dev(6))
									break;
								System.out.println("Sentencia 12");
							}
							finally {
								System.out.println("Sentencia 13");
								if (dev(7))
									break;
								System.out.println("Sentencia 14");					
							}
							System.out.println("Sentencia 15");					
						}
						
						System.out.println("Sentencia 16");	
						System.out.println("Sentencia 18");				
					}
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(5, v.getBooleanFlag(), "Deberían haber 5 booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testNestedTries_ejemplo52() throws Exception {

	    String input = """
	        public class A {
				public boolean [] vec = {false, false, false, false, false, false}; // c2, c3... c7
				
				public boolean dev(int pos) {
					return vec[pos - 2];
				}
				
				public void m() {
					int i = 0;
					while (i < 2) {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
						try {
							System.out.println("Sentencia 3");				
							if (dev(2))
								break;
							System.out.println("Sentencia 4");					
							// Try interno
							try {
								System.out.println("Sentencia 5");				
								if (dev(3))
									break;
								System.out.println("Sentencia 6");					
							}
							catch(Exception e) {
								System.out.println("Tome excepcion " + e);	
								System.out.println("Sentencia 7");
								if (dev(4))
									break;
								System.out.println("Sentencia 8");
							}
							finally {
								System.out.println("Sentencia 9");
								if (dev(5))
									break;
								System.out.println("Sentencia 10");					
							}
							System.out.println("Sentencia 11");	
						}
						// Vuelve al catch externo
						catch(Exception e) {
							System.out.println("Tome excepcion " + e);	
							System.out.println("Sentencia 12");
							if (dev(6))
								break;
							System.out.println("Sentencia 13");
						}
						finally {
							System.out.println("Sentencia 14");
							if (dev(7))
								break;
							System.out.println("Sentencia 15");					
						}
						
						System.out.println("Sentencia 16");	
						System.out.println("Sentencia 18");				
					}
					
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(3, v.getBooleanFlag(), "Deberían haber tres booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testParallelExceptions_ejemplo51() throws Exception {

	    String input = """
	        public class A {
				public boolean [] vec = {false, false, false, false, false, false}; // c2, c3... c7
				
				public boolean dev(int pos) {
					return vec[pos - 2];
				}
				
				public void m() {
					int i = 0;
					while (i < 2) {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
						try {
							System.out.println("Sentencia 3");				
							if (dev(2))
								break;
							System.out.println("Sentencia 4");					
						}
						catch(Exception e) {
							System.out.println("Tome excepcion " + e);	
							System.out.println("Sentencia 5");
							if (dev(3))
								break;
						}
						finally {
							System.out.println("Sentencia 6");
							if (dev(4))
								break;
							System.out.println("Sentencia 7");					
						}
						System.out.println("Sentencia 8");	
						System.out.println("Sentencia 9");
						try {
							System.out.println("Sentencia 10");				
							if (dev(5))
								break;
							System.out.println("Sentencia 11");					
						}
						catch(Exception e) {
							System.out.println("Tome excepcion " + e);	
							System.out.println("Sentencia 12");
							if (dev(6))
								break;
							System.out.println("Sentencia 13");
						}
						finally {
							System.out.println("Sentencia 14");
							if (dev(7))
								break;
							System.out.println("Sentencia 15");					
						}
						
						System.out.println("Sentencia 16");	
						System.out.println("Sentencia 18");				
					}
					
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(3, v.getBooleanFlag(), "Deberían haber tres booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testParallelExceptions_ejemplo50() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 1, y = 3, u = 1, v = 2; 
					int i = 0; 
					while (i < 5) { 
						System.out.println("============ Comienza la vuelta ====================");
						try { 
							i++; 
							int z = y/x; 
							System.out.println("i: " + i + ": " + y + "/" + x + " = " + z); 
							if (x == 2) { 
								System.out.println("Salgo con un break 1"); 
								break; 
							} 
							x--; 
						} 
						catch (Exception e) { 
							System.out.println("hubo un error 1 " + e); 
						} 
						finally { 
							System.out.println("Entro al finally 1"); 
							if (i == 2) 
								break; 
							System.out.println("Salgo del finally 1"); 
						} 
						
						System.out.println("-------- El 2do try -------------------");
						try { 
							int w = v/u; 
							System.out.println("i: " + i + ": " + v + "/" + u + " = " + w); 
							if (u == 2) { 
								System.out.println("Salgo con un break 2"); 
								break; 
							} 
							u--; 
						} 
						catch (Exception e) { 
							System.out.println("hubo un error 2 " + e); 
						} 
						finally { 
							System.out.println("Entro al finally 2"); 
							if (i == 1) 
								break; 
							System.out.println("Salgo del finally 2"); 
						}
						System.out.println("-------- Final de un ciclo -------------------");			
					}	
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber ningún break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(3, v.getBooleanFlag(), "Deberían haber tres booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	

	@Test
	void testBreakBelongingSwitchWithDefaultAndExtraBreak_ejemplo45c() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 1, i = 1;
					while (x < 5) {
						switch(i) {
						case 1: System.out.println("uno"); break;
						case 2: System.out.println("dos"); break;
						case 3: System.out.println("tres"); break;
						default: System.out.println("otro valor"); break;
						}
						if (x == 3)
	    		  			break;
	    		 		System.out.println("Salgo del ciclo");
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
	        () -> assertEquals(4, v.getBreakCount(), "Debería haber cuatro breaks"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "No debería haber una booleana"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener una condición AND")
	    );
	}
	
	
	@Test
	void testBreakBelongingSwitchWithDefault_ejemplo45b() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 1, i = 1;
					while (x < 5) {
						switch(i) {
						case 1: System.out.println("uno"); break;
						case 2: System.out.println("dos"); break;
						case 3: System.out.println("tres"); break;
						default: System.out.println("otro valor"); break;
						}
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
	        () -> assertEquals(4, v.getBreakCount(), "Debería haber cuatro breaks"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No debería haber booleanas")
	    );
	}
	
	
	@Test
	void testBreakBelongingSwitch_ejemplo45a() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 1, i = 1;
					while (x < 5) {
						switch(i) {
						case 1: System.out.println("uno"); break;
						case 2: System.out.println("dos"); break;
						case 3: System.out.println("tres"); break;
						}
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
	        () -> assertEquals(3, v.getBreakCount(), "Debería haber tres breaks"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No debería haber booleanas")
	    );
	}
	
	
	@Test
	void testTryAndFinallyBreaks_ejemplo44() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 2, y = 3;
					int i = 0;
					while (i < 5) {
						try {
							i++; 
							int z = y/x;
							System.out.println("i: " + i + " - Divido " + y + " / " + x + " = " + z);
							if (x == 1) {
								System.out.println("Pego el salto");
								break;
							}
							x--;
						} 
						catch (Exception e) {
							System.out.println("hubo un error " + e);
						}
						finally {
							System.out.println("Entro al finally");
							if (i == 2)
								break;
							System.out.println("Salgo del finally");
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Deberían haber dos booleanas")
	    );
	}
	

	@Test
	void testTryTwoCatchs_ejemplo43() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int i = 0, x = 0;
					while (i < 0) {
						try {
							System.out.println("i: " + i);
							if (x % 3 == 0)
								break;
							x++;
							i++;
						}
						catch (ArithmeticException e) {
							System.out.println("hubo un error");
							if (x % 4 == 0)
								break;
							i--;
						}
						catch (Exception e) {
							System.out.println("hubo un error");
							if (x % 5 == 0)
								break;
							i--;
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	
	@Test
	void testWhileInsideTry_ejemplo41() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int i = 0, x = 0;
					try {
						while (i < 0) {
							System.out.println("i: " + i);
							if (x % 3 == 0)
								break;
							x++;
							i++;
						}
					}
					catch (Exception e) {
						System.out.println("hubo un error");
					}
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Deberían haber una booleana")
	    );
	}
	
	
	@Test
	void testMixedForWithWhile_ejemplo39() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 0;
					for (int i = 0; i < 10; i++) {
						System.out.println("hola");
						if (i == 5)
							if (x % 2 == 0)
								break;
						x++;
						while (x < 10) {
							if (x == 7)
								break;
							x *= 2;
						}
						System.out.println("fin for");
					}
					System.out.println("fin m");
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(2, v.getWhileCount(), "Deberían haber dos whiles"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Deberían haber dos booleanas")
	    );
	}
	
	
	@Test
	void testMixedBreakWithContinue_ejemplo35() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					while (x < 10) {
						int y = 10;
						double w;
						
						if (x == 5) {
							if (z % 2 == 0) {
								if (cond == 3) {
									System.out.println(cond); 
									continue;
								}
								w = 3.14;
							}
							x++;
						} else
							break;
						z++;
						y++;
					 }
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Deberían haber dos booleanas")
	    );
	}
	
	
	@Test
	void testNestedDoWhileInWhile_ejemplo34() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					while (x < 10) {
						int y = 10;
						double w = 0.0;
			
						do {
							w += 1.2;
							if (w >= 8.4)
								break;
							w -= 0.5;
						} while (w < 20.0);
						
						if (x == 5) {
							if (z % 2 == 0) {
								if (cond == 3) {
									System.out.println(cond); 
									break;
								}
								else
									if (y < 10)
										break;
								w = 3.14;
							}
							x++;
						}
						z++;
						y++;
					 }
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getDoCount(), "Debería haber un do-while"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Deberían haber dos booleanas")
	    );
	}
	

	@Test
	void testParallelWhilesBreaks_ejemplo27() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 0;
					int y = 0;
					int z = 0;
					while (x < 10) {
						x++;
						System.out.println("x: " + x);
						if (x == 5) {
							if (y % 2 == 0)
								break;
							System.out.println("y: " + y);
						}
						y++;
					}
			
					System.out.println("z: " + z);
					while (y < 10) {
						System.out.println("y: " + y);
						if (y == 8)
							break;
						y++;
					}
				}
	        }
	        """;
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(2, v.getWhileCount(), "Deberían haber dos whiles"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Deberían haber dos booleanas")
	    );
	}
	
	
	@Test
	void testManyBreaks_ejemplo25() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 0;
					int y = 0;
					int z = 0;
					while (x < 10) {
						x++;
						System.out.println("x: " + x);
						if (x == 5) {
							if (y % 2 == 0)
								break;
							System.out.println("y: " + y);
						}
						else {
							y++;
							break;
						}
						System.out.println("Despues del else");
						if (z == 8)
							break;
						System.out.println("z: " + z);
					}
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	
	@Test
	void testSimpleExceptionBreakInFinally_ejemplo22() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 5, y = 0, z;
					
					while (true) {
						try {
							z = x / y;
						}
						catch(Exception e) {
							System.out.println("error: " + e);
							//break;
						}
						finally {
							x++;
							if (x > 5)
								break;
							y++;
							System.out.println("x: " + x + " y: " + y);
						}
						
						System.out.println("Algo mas");
					}
					System.out.println("cierre");
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	
	@Test
	void testSimpleExceptionBreakInCatch_ejemplo21() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 5, y = 0, z;
					
					while (true) {
						try {
							z = x / y;
						}
						catch(Exception e) {
							System.out.println("error: " + e);
							break;
						}
						System.out.println("Algo mas");
					}
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(1, v.getBreakCount(), "Debería estar el break original"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No debería haber ninguna booleana")
	    );
	}
	
	
	@Test
	void testSimpleExceptionInWhile_ejemplo20() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int x = 5, y = 0, z;
					
					while (true) {
						try {
							z = x / y;
							break;
						}
						catch(Exception e) {
							System.out.println("error: " + e);
						}
						System.out.println("Algo mas");
					}
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
		        () -> assertEquals(1, v.getBreakCount(), "Debería estar el break original"),
		        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
		        () -> assertEquals(0, v.getBooleanFlag(), "No debería haber ninguna booleana")
	    );
	}
	
	
	@Test
	void testWhileWithNestedContinue_ejemplo19() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					while (x < 10) {
						int y = 10;
						double w;
						
						if (x == 5) {
							if (z % 2 == 0) {
								if (cond == 3) {
									System.out.println(cond); 
									continue;
								}
								w = 3.14;
							}
							x++;
						}
						z++;
						y++;
					 }
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continues"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}

	
	@Test
	void testDoWhileWithBreak_ejemplo18() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					int i = 0;
					do {
						int y = 10;
						double w;
						
						if (x == 5) {
							if (z % 2 == 0) {
								if (cond == 3) {
									System.out.println(cond); 
								} else
									break;
								w = 3.14;
							}
							x++;
						}
						z++;
						y++;
						i++;
					 } while (i < 10);
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(1, v.getDoCount(), "Debería haber un do-while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	
	@Test
	void testWhileWithBreakInElse_ejemplo14() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					while (x < 10) {
						int y = 10;
						double w;
						
						if (x == 5) {
							if (z % 2 == 0) {
								if (cond == 3) {
									System.out.println(cond); 
								}
								else
									break;
								w = 3.14;
							}
							x++;
						}
						z++;
						y++;
					 }
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	
	@Test
	void testWhileWithNested3If_ejemplo13() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					while (x < 10) {
						int y = 10;
						double w;
						
						if (x == 5) {
							if (z % 2 == 0) {
								if (cond == 3) {
									System.out.println(cond); 
									break;
								}
								w = 3.14;
							}
							x++;
						}
						z++;
						y++;
					 }
					x--;
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	

	@Test
	void testWhileWithNestedIf_ejemplo10() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					int x = 5;
					
					while (x < 10) {
						int y = 10;
						double w;
						
						if (x == 5) {
							if (z % 2 == 0) {
								break;
							}
							x++;
						}
						z++;
						y++;
					 }
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	
	@Test
	void testdWhileWithTwoIfsAtTheSameLevel_ejemplo9() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					int cond = 3;
					
					while (x < 10) {
						int y = 10;
						double w;
						
						if (x == 5)
							break;
						
						if (z % 2 == 0)
							break;
						
						w = 6.2;
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber una booleana")
	    );
	}
	
	@Test
	void testNestedWhileWithtBreak_ejemplo8() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int z = 6;
					System.out.println(z);
					int x = 5;
					
					while (x < 10) {
						
						int y = 10;
						double w;
						
						if (x == 5)
							break;
						else {
							if (y == 10)
								System.out.println(y);
						}
						
						x++;
						while (z < 10) {
							if (z % 2 == 0)
								break;
							x--;
						}
						
						w = 6.2;
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
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber breaks"),
	        () -> assertEquals(2, v.getWhileCount(), "Debería haber un while"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Debería haber dos booleanas")
	    );
	}
	
	
	@Test
	void testSimpleWhileWithoutBreak_ejemplo4() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int i = 0;
					while (i < 10) {
						if (i == 3) {
							i += 2;
							System.out.println(i);
						}
						i++;
					}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "No debería haber break"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertFalse(v.getHasBooleanFlag(), "No debería haber ninguna booleana")
	    );
	}
	
	@Test
	void testSimpleWhileWithBreak_ejemplo3() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int i = 0;
					while (i < 10) {
						if (i == 3) {
							i += 2;
							System.out.println(i);
							break;
						}
						i++;
					}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getBreakCount(), "Todavía hay un break"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber un while"),
	        () -> assertTrue(v.getHasBooleanFlag(), "No se declaró ninguna booleana"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Deberia haber una booleana"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while no tiene condición con &&")
	    );
	}
	

	@Test
	void testForAndWhileWithBreak_ejemplo2() throws Exception {

	    String input = """
	        public class A {
				public void m() {
					int i = 0;
					while (i < 10) {
						if (i == 3) {
							i += 2;
							System.out.println("hola");
							break;
						}
						i++;
					}
					
					for (i = 0; i < 10; i++) {
						if (i > 3) {
							if (i % 2 == 0) {
								System.out.println("anidado");
							}
						}
						System.out.println("algo");
					}
			
					i = 0;
					while (i < 10) {
						if (i == 5) {
							i += 2;
							System.out.println("otro while con if");
							break;
						}
						i++;
					}
			
					for (i = 0; i < 10; i++) 
						if (i > 3)  break;
					
				}
	        }
	        """;
	    
	    
	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result); 
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(1, v.getForCount(), "Todavía hay un for"),
	        () -> assertEquals(3, v.getWhileCount(), "No se generó while"),
	        () -> assertTrue(v.getHasBooleanFlag(), "No se declaró ninguna booleana"),
	        () -> assertEquals(3, v.getBooleanFlag(), "Deberia haber tres booleanas"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while no tiene condición con &&")
	    );
	}
	
	
	@Test
	void testStructural_ForToWhileWithBreak() throws Exception {

	    String input = """
	        public class A {
	            void m() {
	                for(int i=0; i<10; i++) {
	                    if(i==5) break;
	                    System.out.println(i);
	                }
	            }
	        }
	        """;

	    String result = RefactoringEngine.runFullPipeline(input);
	    CompilationUnit cu = CompilationTestUnit.parse(result);
	    StructureVisitor v = new StructureVisitor();
	    cu.accept(v);

	    assertAll("Estructura del AST",
	        () -> assertEquals(0, v.getForCount(), "Todavía hay un for"),
	        () -> assertEquals(1, v.getWhileCount(), "No se generó while"),
	        () -> assertTrue(v.getHasBooleanFlag(), "No se declaró la variable booleana"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while no tiene condición con &&")
	    );
	}
	
	
	@Test
	void testForEachToWhileWithBreak() throws Exception {
	    String input = "public class A { void m() { String nombres[] = {\"Juan\", \"Silvia\", \"Ramon\", \"Laura\"}; "
	    		+ "for(String s: nombres) { System.out.println(s); if(s.equalsIgnoreCase(\"Ramon\")) break; } } }";
	    String result = RefactoringEngine.runFullPipeline(input);

	    assertAll("Validación de transformación",
	        () -> assertFalse(result.contains("for("), "Error: El 'for' sigue presente"),
	        () -> assertTrue(result.contains("while"), "Error: No se encontró el 'while'"),
	        () -> assertTrue(result.contains("System.out.println(s)"), "Error: Se perdió la impresion"),
	        () -> assertTrue(result.contains("boolean stay1"), "Error: no se definio la variable booleana")
	    );
	}	

	
	@Test
	void testForToWhileWithBreak() throws Exception {
	    String input = "public class A { void m() { for(int i=0; i<10; i++) { if(i==5) break; System.out.println(i);} } }";
	    String result = RefactoringEngine.runFullPipeline(input);

	    assertAll("Validación de transformación",
	        () -> assertFalse(result.contains("for("), "Error: El 'for' sigue presente"),
	        () -> assertTrue(result.contains("while"), "Error: No se encontró el 'while'"),
	        () -> assertTrue(result.contains("i++"), "Error: Se perdió el incremento")
	    );
	}	
	
	
	@Test
	void testShouldNotTransformSimpleFor() throws Exception {
	    String input = "public class A { void m() { for(int i=0; i<10; i++) { i--; } } }";
	    String result = RefactoringEngine.runFullPipeline(input);
	    
	    // Aquí el éxito es que el resultado sea IGUAL al input
	    assertEquals(input.replaceAll("\\s+", ""), result.replaceAll("\\s+", ""), 
	        "El código no debería haber cambiado porque no hay breaks");
	}

}
