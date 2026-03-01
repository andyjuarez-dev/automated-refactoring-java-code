package uma.master.isia.refactoring;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import uma.master.isia.refactoring.util.CompilationTestUnit;
import uma.master.isia.refactoring.util.StructureVisitor;

public class PruebaTest {
	
	@Test
	void testTwoEnhancedForWithAndWithoutBreak() throws Exception {

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
					
					for (int i : vector) {
						System.out.println(x);
						if (i == 5)
							System.out.println("hola");
					}

					for (int i : vector) {
						System.out.println(x);
						if (i == 5)
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
	        () -> assertEquals(1, v.getEnhancedForCount(), "Debería haber 1 foreach"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber 1 booleana")
	    );
	}
	
	
	@Test
	void testEnhancedForWithBreak2() throws Exception {

	    String input = """
	    public class A {
				class Numeros {
					private int[] valores;
					
					public Numeros(int t) {
						valores = new int[t];
					}
					
					public void agregar(int v, int p) {
						valores[p] = v;
					}
					
					public int[] obtener() {
						return valores;
					}
				}
				
				void m() {
					Numeros num = new Numeros(3);
					num.agregar(8, 0);
					num.agregar(5, 1);		
					num.agregar(6, 2);
					
					for (int i: num.obtener()) {
						System.out.println(i);
						if (i == 5)
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
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber 1 booleana"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
	    );
	}
	
	
	@Test
	void testEnhancedForWithBreak() throws Exception {

	    String input = """
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
				
				class Personas {
					private Persona[] personas;
					
					public Personas(int t) {
						personas = new Persona[t];
					}
					
					public void agregar(String n, int e, int p) {
						personas[p] = new Persona(n, e);
					}
					
					public Persona[] obtener() {
						return personas;
					}
				}
				
				void m() {
					Personas p = new Personas(3);
					p.agregar("Juan", 20, 0);
					p.agregar("Juan", 20, 1);
					p.agregar("Juan", 20, 2);
					
					for (Persona pers: p.obtener()) {
						System.out.println(pers.getNombre());
						if (pers.getEdad() == 5)
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
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber 1 booleana"),
	        () -> assertTrue(v.getWhileHasAndCondition(), "El while debería tener AND")
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
						for (int i : vector) {
							System.out.println(x);
							if (i == 5)
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
	        () -> assertEquals(1, v.getEnhancedForCount(), "Debería haber 1 foreach"),
	        () -> assertEquals(0, v.getWhileCount(), "No deberían haber whiles"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No deberían haber booleanas")
	    );
	}
	
	
	@Test
	void testFinally2WithContinue() throws Exception {

	    String input = """
        public class A {
			
			void m() {
				int i = 0;
				int x = 0;
				int w = 0;
				if (w == 0) {
					do {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
	    				if (x == 3) continue;	
						System.out.println("Sentencia 16");	
						System.out.println("Sentencia 18");				
					} while (i < 5);
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
	        () -> assertEquals(1, v.getDoCount(), "Debería haber 1 do-while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber 1 booleana")
	    );
	}

	@Test
	void testFinallyWithLabelContinue() throws Exception {

	    String input = """
        public class A {
				
				void m() {
					int i = 0;
					int x = 0;
					outer:
					for (int i = 0; i < 5; i++) {
						while (x < 2) {
							x++;
							System.out.println("Sentencia 1");
							System.out.println("Sentencia 2");
							try {
								System.out.println("Sentencia 3");				
								System.out.println("Sentencia 4");
								if (x == 1) 
								    System.out.println("Sentencia 5");					
							}
							catch(Exception e) {
		    		  		    System.out.println(x);
		    		 		}						
		    		 		finally {
								System.out.println("Sentencia 7");
								System.out.println("Sentencia 8");
								if (x == 3)
								     continue outer;
								System.out.println("Sentencia 15");					
							}
							
							System.out.println("Sentencia 16");	
							System.out.println("Sentencia 18");				
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
	        () -> assertEquals(0, v.getBreakCount(), "No deberían haber breaks"),
	        () -> assertEquals(1, v.getContinueCount(), "Debería haber 1 continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No deberían haber booleanas")
	    );
	}
	
	
	@Test
	void testFinallyWithLabelBreak() throws Exception {

	    String input = """
        public class A {
				
				void m() {
					int i = 0;
					int x = 0;
					outer:
					for (int i = 0; i < 5; i++) {
						while (x < 2) {
							x++;
							System.out.println("Sentencia 1");
							System.out.println("Sentencia 2");
							try {
								System.out.println("Sentencia 3");				
								System.out.println("Sentencia 4");
								if (x == 1) 
								    System.out.println("Sentencia 5");					
							}
							catch(Exception e) {
		    		  		    System.out.println(x);
		    		 		}						
		    		 		finally {
								System.out.println("Sentencia 7");
								System.out.println("Sentencia 8");
								if (x == 3)
								     break outer;
								System.out.println("Sentencia 15");					
							}
							
							System.out.println("Sentencia 16");	
							System.out.println("Sentencia 18");				
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
	        () -> assertEquals(1, v.getBreakCount(), "Debería haber 1 break"),
	        () -> assertEquals(0, v.getContinueCount(), "No debería haber continue"),
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(0, v.getBooleanFlag(), "No deberían haber booleanas")
	    );
	}
	
	
	@Test
	void testFinallyWithContinue() throws Exception {

	    String input = """
        public class A {
				
				void m() {
					int i = 0;
					int x = 0;
					int w = 0;
					if (w == 0) 
						while (i < 2) {
							i++;
							System.out.println("Sentencia 1");
							System.out.println("Sentencia 2");
							try {
								System.out.println("Sentencia 3");				
								System.out.println("Sentencia 4");					
							}
							// Vuelve al catch externo
							catch(Exception e) {
	    		  		    	if (w == 3)
		    		  		    	   break;
	   	    		  		    for (x = 0; x < 5; x++) {
		    		  		    	System.out.println(x);
		    		  		    }
		    		 		}						
		    		 		finally {
								System.out.println("Sentencia 7");
								System.out.println("Sentencia 8");
								if (x == 3)
								     continue;
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
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(2, v.getBooleanFlag(), "Deberían haber 2 booleanas")
	    );
	}
	
	
	@Test
	void testSomeEnhancedFor_ejemplo59() throws Exception {

	    String input = """
        public class A {
				public boolean [] vec = {false, false, false, false, false, false}; // c2, c3... c7
				
				public boolean dev(int pos) {
					return vec[pos - 2];
				}
				
				void m() {
					int i = 0;
					while (i < 2) {
						i++;
						System.out.println("Sentencia 1");
						System.out.println("Sentencia 2");
						try {
							System.out.println("Sentencia 3");				
							System.out.println("Sentencia 4");					
						}
						// Vuelve al catch externo
						catch(Exception e) {
    		  		    	if (x == 3)
	    		  		    	   break;
   	    		  		    for (int x = 0; x < 5; x++) {
	    		  		    	System.out.println(x);
	    		  		    }
	    		 		}						
	    		 		finally {
							System.out.println("Sentencia 7");
							System.out.println("Sentencia 8");
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
	        () -> assertEquals(1, v.getWhileCount(), "Debería haber 1 while"),
	        () -> assertEquals(1, v.getBooleanFlag(), "Debería haber 1 booleana")
	    );
	}
	

	@Test
	void testSomeEnhancedFor_ejemplo58() throws Exception {

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
						for (int i : vector) {
							System.out.println(x);
							if (i == 5)
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
	void testSomeEnhancedFor_ejemplo57() throws Exception {

	    String input = """
        public class A {
			void m() {
					int [] vector = new int[5];
					vector[0] = 8;
					vector[1] = 8;
					vector[2] = 8;
					vector[3] = 8;
					vector[4] = 8;
					
					// CASO 1: array primitivo
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
	
}
