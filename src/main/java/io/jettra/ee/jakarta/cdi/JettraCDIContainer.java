package io.jettra.ee.jakarta.cdi;

import io.jettra.ee.core.IO;
import io.jettra.ee.microprofile.config.JettraMPConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contenedor Jakarta CDI 4.1 ultra-ligero y de alto rendimiento para JettraEE.
 * Soporta inyección de dependencias (@Inject), ámbitos (@ApplicationScoped, @Singleton, @RequestScoped),
 * inyección de configuración (@ConfigProperty), métodos productores y ciclo de vida (@PostConstruct, @PreDestroy).
 */
public class JettraCDIContainer extends CDI<Object> implements CDIProvider {

    private static final JettraCDIContainer INSTANCE = new JettraCDIContainer();

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Set<Class<?>> managedBeanClasses = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Map<Class<?>, Object>> requestScopeStore = ThreadLocal.withInitial(HashMap::new);

    public JettraCDIContainer() {
    }

    public static JettraCDIContainer getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        CDI.setCDIProvider(INSTANCE);
    }

    /**
     * Detecta el modo de descubrimiento de beans configurado en beans.xml
     * (src/main/webapp/WEB-INF/beans.xml o src/main/resources/META-INF/beans.xml).
     */
    public static String getBeanDiscoveryMode() {
        String[] possibleBeansXml = new String[]{
                "src/main/webapp/WEB-INF/beans.xml",
                "src/main/resources/META-INF/beans.xml",
                "WEB-INF/beans.xml",
                "META-INF/beans.xml"
        };
        for (String path : possibleBeansXml) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && !f.isDirectory()) {
                try {
                    String content = java.nio.file.Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("bean-discovery-mode\\s*=\\s*\"([^\"]+)\"").matcher(content);
                    if (m.find()) {
                        return m.group(1).trim();
                    }
                    return "annotated";
                } catch (Exception ignored) {}
            }
        }
        return "annotated";
    }

    /**
     * Registra una clase administrada por CDI.
     */
    public void registerBean(Class<?> clazz) {
        if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
            managedBeanClasses.add(clazz);
        }
    }

    /**
     * Registra una instancia explícita como singleton.
     */
    public <T> void registerSingleton(Class<T> clazz, T instance) {
        singletons.put(clazz, instance);
        managedBeanClasses.add(clazz);
    }

    /**
     * Obtiene una instancia del bean solicitado, respetando su ámbito.
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        // 1. Si es Config de MicroProfile
        if (Config.class.isAssignableFrom(clazz)) {
            return (T) ConfigProvider.getConfig();
        }

        // 2. Si ya existe como singleton
        if (singletons.containsKey(clazz)) {
            return (T) singletons.get(clazz);
        }

        // 3. Si coincide con una interfaz ya implementada en singletons
        for (Map.Entry<Class<?>, Object> entry : singletons.entrySet()) {
            if (clazz.isAssignableFrom(entry.getKey())) {
                return (T) entry.getValue();
            }
        }

        // 3b. Si es una interfaz, buscar una clase administrada que la implemente
        if (clazz.isInterface()) {
            for (Class<?> implClass : managedBeanClasses) {
                if (clazz.isAssignableFrom(implClass)) {
                    return (T) getBean(implClass);
                }
            }
        }

        // 4. Verificar RequestScoped
        if (clazz.isAnnotationPresent(RequestScoped.class)) {
            Map<Class<?>, Object> requestStore = requestScopeStore.get();
            if (requestStore.containsKey(clazz)) {
                return (T) requestStore.get(clazz);
            }
            T newReqInstance = createInstance(clazz);
            requestStore.put(clazz, newReqInstance);
            return newReqInstance;
        }

        // 5. Verificar ApplicationScoped o Singleton
        boolean isApplicationScoped = clazz.isAnnotationPresent(ApplicationScoped.class)
                || clazz.isAnnotationPresent(Singleton.class)
                || !clazz.isAnnotationPresent(RequestScoped.class); // default lightweight scope

        if (isApplicationScoped) {
            synchronized (singletons) {
                if (singletons.containsKey(clazz)) {
                    return (T) singletons.get(clazz);
                }
                T instance = createInstance(clazz);
                singletons.put(clazz, instance);
                return instance;
            }
        }

        // 6. Dependent / Transient
        return createInstance(clazz);
    }

    /**
     * Obtiene una instancia del bean por su nombre @Named o nombre de clase simple.
     */
    public Object getBeanByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (Class<?> clazz : managedBeanClasses) {
            String beanName = null;
            if (clazz.isAnnotationPresent(jakarta.inject.Named.class)) {
                jakarta.inject.Named namedAnn = clazz.getAnnotation(jakarta.inject.Named.class);
                beanName = namedAnn.value();
            }
            if (beanName == null || beanName.isEmpty()) {
                String simple = clazz.getSimpleName();
                beanName = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
            }
            if (name.equalsIgnoreCase(beanName)) {
                return getBean(clazz);
            }
        }
        for (Map.Entry<Class<?>, Object> entry : singletons.entrySet()) {
            String simple = entry.getKey().getSimpleName();
            String beanName = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
            if (name.equalsIgnoreCase(beanName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Crea una instancia de la clase, inyecta sus dependencias e invoca @PostConstruct.
     */
    @SuppressWarnings("unchecked")
    public <T> T createInstance(Class<T> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();
            inject(instance);
            invokePostConstruct(instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Error instanciando bean CDI: " + clazz.getName(), e);
        }
    }

    /**
     * Inyecta dependencias (@Inject, @ConfigProperty) en los campos de una instancia.
     */
    public void inject(Object instance) {
        if (instance == null) return;
        Class<?> current = instance.getClass();
        Config config = null;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);

                // 1. Inyección de @ConfigProperty (Eclipse MicroProfile Config)
                if (field.isAnnotationPresent(ConfigProperty.class)) {
                    if (config == null) {
                        config = ConfigProvider.getConfig();
                    }
                    ConfigProperty cp = field.getAnnotation(ConfigProperty.class);
                    String propName = cp.name();
                    String defaultVal = cp.defaultValue();
                    Object valueToInject = resolveConfigValue(config, field.getType(), propName, defaultVal);
                    try {
                        if (valueToInject != null) {
                            field.set(instance, valueToInject);
                        }
                    } catch (Exception e) {
                        IO.error("Error inyectando @ConfigProperty en campo " + field.getName() + " de " + current.getName(), e);
                    }
                }
                // 2. Inyección de @Inject (Jakarta CDI o Jettra @Inject)
                else if (field.isAnnotationPresent(Inject.class) || hasInjectAnnotation(field)) {
                    Class<?> fieldType = field.getType();
                    try {
                        Object dep = getBean(fieldType);
                        field.set(instance, dep);
                    } catch (Exception e) {
                        IO.error("Error inyectando @Inject en campo " + field.getName() + " de " + current.getName(), e);
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    private boolean hasInjectAnnotation(Field field) {
        for (Annotation ann : field.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals("Inject")) {
                return true;
            }
        }
        return false;
    }

    private Object resolveConfigValue(Config config, Class<?> type, String name, String defaultValue) {
        try {
            Optional<?> opt = config.getOptionalValue(name, type);
            if (opt.isPresent()) {
                return opt.get();
            }
        } catch (Exception ignored) {}

        if (defaultValue != null && !defaultValue.equals(ConfigProperty.UNCONFIGURED_VALUE)) {
            if (type == String.class) return defaultValue;
            if (type == Integer.class || type == int.class) return Integer.parseInt(defaultValue);
            if (type == Long.class || type == long.class) return Long.parseLong(defaultValue);
            if (type == Double.class || type == double.class) return Double.parseDouble(defaultValue);
            if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(defaultValue);
        }
        return null;
    }

    private void invokePostConstruct(Object instance) {
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class) && method.getParameterCount() == 0) {
                    try {
                        method.setAccessible(true);
                        method.invoke(instance);
                    } catch (Exception e) {
                        IO.error("Error ejecutando @PostConstruct en " + instance.getClass().getName(), e);
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    public void invokePreDestroyAll() {
        for (Object instance : singletons.values()) {
            Class<?> current = instance.getClass();
            while (current != null && current != Object.class) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(PreDestroy.class) && method.getParameterCount() == 0) {
                        try {
                            method.setAccessible(true);
                            method.invoke(instance);
                        } catch (Exception e) {
                            IO.error("Error ejecutando @PreDestroy en " + instance.getClass().getName(), e);
                        }
                    }
                }
                current = current.getSuperclass();
            }
        }
        singletons.clear();
        managedBeanClasses.clear();
    }

    public void clearRequestScope() {
        requestScopeStore.remove();
    }

    // --- Implementación de CDIProvider y CDI<Object> ---

    @Override
    public CDI<Object> getCDI() {
        return this;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Instance<Object> select(Annotation... qualifiers) {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return new Instance<U>() {
            @Override
            public Instance<U> select(Annotation... qualifiers) { return this; }
            @Override
            public <U1 extends U> Instance<U1> select(Class<U1> subtype, Annotation... qualifiers) {
                return JettraCDIContainer.this.select(subtype, qualifiers);
            }
            @Override
            public <U1 extends U> Instance<U1> select(TypeLiteral<U1> subtype, Annotation... qualifiers) {
                return null;
            }
            @Override
            public boolean isUnsatisfied() { return false; }
            @Override
            public boolean isAmbiguous() { return false; }
            @Override
            public void destroy(U instance) {}
            @Override
            public Handle<U> getHandle() { return null; }
            @Override
            public Iterable<? extends Handle<U>> handles() { return Collections.emptyList(); }
            @Override
            public Iterator<U> iterator() { return Collections.singletonList(get()).iterator(); }
            @Override
            public U get() { return getBean(subtype); }
        };
    }

    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return null;
    }

    @Override
    public boolean isUnsatisfied() { return false; }
    @Override
    public boolean isAmbiguous() { return false; }
    @Override
    public void destroy(Object instance) {}
    @Override
    public Handle<Object> getHandle() { return null; }
    @Override
    public Iterable<? extends Handle<Object>> handles() { return Collections.emptyList(); }
    @Override
    public Iterator<Object> iterator() { return singletons.values().iterator(); }
    @Override
    public Object get() { return this; }

    @Override
    public BeanManager getBeanManager() { return null; }

    @Override
    public BeanContainer getBeanContainer() { return null; }
}
