package uma.master.isia.refactoring.util;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


public class CompilationTestHelper {

    public static void assertCompiles(String className, String source) throws Exception {
        Path dir = Files.createTempDirectory("compileTest");
        Path file = dir.resolve(className + ".java");

        Files.writeString(file, source);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "No hay JDK configurado (falta tools.jar / JDK)");

        int result = compiler.run(null, null, null, file.toString());

        assertEquals(0, result, "El código refactorizado NO compila");
    }
	
}
