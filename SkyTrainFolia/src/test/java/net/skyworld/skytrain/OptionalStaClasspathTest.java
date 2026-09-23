package net.skyworld.skytrain;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.*;
import java.util.jar.JarFile;
import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;

/** Runs a separate JVM, not a mock provider on a classpath that still contains STA. */
public final class OptionalStaClasspathTest {
    public static void main(String[] args) throws Exception {
        if(args.length==0) {
            List<String> entries=new ArrayList<>();
            for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path path=Path.of(entry).toAbsolutePath();
                boolean sta;
                if(Files.isDirectory(path)) sta=Files.exists(path.resolve("net/skyworld/sta"));
                else try(var jar=new JarFile(path.toFile())) {
                    sta=jar.stream().anyMatch(e->e.getName().startsWith("net/skyworld/sta/"));
                }
                if(!sta) entries.add(path.toString());
            }
            String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java").toString();
            var process=new ProcessBuilder(javaExecutable,"-ea","-cp",String.join(File.pathSeparator,entries),
                    OptionalStaClasspathTest.class.getName(),"without-sta").inheritIO().start();
            if(!process.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                throw new AssertionError("Standalone JVM timed out");
            }
            assert process.exitValue()==0 : "Standalone JVM failed";
            return;
        }
        try { Class.forName("net.skyworld.sta.api.v5.DriverDeskService"); throw new AssertionError("STA still present"); }
        catch(ClassNotFoundException expected) { }
        Path root=Path.of(Train.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        int scanned=0;
        try(var files=Files.walk(root.resolve("net/skyworld/skytrain"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()) {
                String name=root.relativize(file).toString().replace(File.separatorChar,'.').replaceAll("\\.class$","");
                if(name.startsWith("net.skyworld.skytrain.StaTelemetryPublisher")
                        || name.startsWith("net.skyworld.skytrain.StaCabIntegration")) continue;
                String bytes=new String(Files.readAllBytes(file),StandardCharsets.ISO_8859_1);
                assert !bytes.contains("net/skyworld/sta/") && !bytes.contains("net.skyworld.sta.") : name;
                Class<?> type=Class.forName(name,false,OptionalStaClasspathTest.class.getClassLoader());
                type.getGenericSuperclass(); type.getGenericInterfaces(); type.getDeclaredAnnotations();
                for(var field:type.getDeclaredFields()) field.getGenericType();
                for(var method:type.getDeclaredMethods()) {
                    method.getGenericReturnType(); method.getGenericParameterTypes(); method.getGenericExceptionTypes();
                    method.getDeclaredAnnotations();
                }
                type.getMethods();
                for(var constructor:type.getDeclaredConstructors()) constructor.getGenericParameterTypes();
                scanned++;
            }
        }
        assert TelemetrySink.create(null,()->{throw new AssertionError("Missing STA invoked factory");})==null;
        Plugin disabled=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class[]{Plugin.class},
                (p,m,a)->m.getName().equals("isEnabled")?false:null);
        assert TelemetrySink.create(disabled,()->{throw new AssertionError("Disabled STA invoked factory");})==null;
        var fallback=new TelemetrySink() {};
        assert !fallback.cabAuthority(UUID.randomUUID(),null,1000).live();
        assert fallback.stationAhead(null,256)==null && fallback.query(null)==null;
        fallback.close();
        assert scanned>50;
        System.out.println("PASS without STA: "+scanned+" core classes/signatures/annotations, listener reflection and lazy/no-op integration");
    }
}
