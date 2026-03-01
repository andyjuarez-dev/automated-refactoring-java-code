package uma.master.isia.refactoring.util;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BehaviorTestHelper {
	
    public static void assertBehaviorPreserved(String className, String original, String refactored) throws Exception {

        String origWithMain = addMain(className, original);
        String refWithMain  = addMain(className, refactored);

        Path dirOrig = Files.createTempDirectory("origRun");
        Path dirRef  = Files.createTempDirectory("refRun");

        compile(dirOrig, className, origWithMain);
        compile(dirRef, className, refWithMain);

        String out1 = run(dirOrig, className);
        String out2 = run(dirRef, className);

        assertEquals(out1, out2, "El comportamiento cambió tras el refactoring");
    }

    private static void compile(Path dir, String className, String src) throws Exception {
        Path file = dir.resolve(className + ".java");
        Files.writeString(file, src);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "No hay JDK configurado");

        int result = compiler.run(null, null, null, file.toString());
        assertEquals(0, result, "Error compilando código de prueba");
    }

    private static String run(Path dir, String className) throws Exception {
        URLClassLoader cl = URLClassLoader.newInstance(new URL[]{dir.toUri().toURL()});
        Class<?> cls = Class.forName(className, true, cl);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));

        Method m = cls.getMethod("main", String[].class);
        m.invoke(null, (Object) new String[]{});

        System.setOut(old);
        return out.toString().replace("\r\n", "\n");
    }

    private static String addMain(String className, String src) {
        return src.replaceFirst("\\}$",
            "public static void main(String[] args){ new " + className + "().m(); }}");
    }
	
}
