package io.jettra.ee.core;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Escáner de clases de alta velocidad para autodescubrimiento de beans CDI,
 * recursos Jakarta REST, extensiones MicroProfile y vistas JettraFlux.
 */
public class ClassScanner {

    private final Set<String> basePackages;
    private final ClassLoader classLoader;

    public ClassScanner(Collection<String> basePackages) {
        this.basePackages = new HashSet<>(basePackages);
        this.classLoader = Thread.currentThread().getContextClassLoader() != null
                ? Thread.currentThread().getContextClassLoader()
                : ClassScanner.class.getClassLoader();
    }

    public ClassScanner(String... basePackages) {
        this(Arrays.asList(basePackages));
    }

    /**
     * Escanea y retorna todas las clases anotadas con la anotación indicada.
     */
    public Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationClass) {
        Set<Class<?>> result = new HashSet<>();
        for (Class<?> clazz : scanClasses()) {
            if (clazz.isAnnotationPresent(annotationClass)) {
                result.add(clazz);
            }
        }
        return result;
    }

    /**
     * Escanea y retorna todas las clases que implementan la interfaz indicada.
     */
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> findSubclassesOf(Class<T> targetInterface) {
        Set<Class<? extends T>> result = new HashSet<>();
        for (Class<?> clazz : scanClasses()) {
            if (targetInterface.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                result.add((Class<? extends T>) clazz);
            }
        }
        return result;
    }

    /**
     * Escanea y retorna todas las clases en los paquetes base configurados.
     */
    public Set<Class<?>> scanClasses() {
        Set<Class<?>> classes = new HashSet<>();
        for (String pkg : basePackages) {
            String path = pkg.replace('.', '/');
            try {
                Enumeration<URL> resources = classLoader.getResources(path);
                while (resources.hasMoreElements()) {
                    URL resource = resources.nextElement();
                    String protocol = resource.getProtocol();
                    if ("file".equals(protocol)) {
                        String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                        findClassesInDirectory(new File(filePath), pkg, classes);
                    } else if ("jar".equals(protocol)) {
                        JarURLConnection jarConn = (JarURLConnection) resource.openConnection();
                        try (JarFile jar = jarConn.getJarFile()) {
                            findClassesInJar(jar, path, classes);
                        }
                    }
                }
            } catch (IOException e) {
                IO.warn("Error escaneando paquete '" + pkg + "': " + e.getMessage());
            }
        }
        return classes;
    }

    private void findClassesInDirectory(File directory, String packageName, Set<Class<?>> classes) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                findClassesInDirectory(file, subPackage, classes);
            } else if (file.getName().endsWith(".class") && !file.getName().matches(".*\\$\\d+\\.class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                loadClass(className, classes);
            }
        }
    }

    private void findClassesInJar(JarFile jarFile, String packagePath, Set<Class<?>> classes) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(packagePath) && name.endsWith(".class") && !name.matches(".*\\$\\d+\\.class")) {
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                loadClass(className, classes);
            }
        }
    }

    private void loadClass(String className, Set<Class<?>> classes) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            classes.add(clazz);
        } catch (Throwable ignored) {
            // Se omiten clases que dependan de módulos o dependencias no presentes
        }
    }
}
