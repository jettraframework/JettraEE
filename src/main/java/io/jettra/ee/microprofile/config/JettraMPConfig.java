package io.jettra.ee.microprofile.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Implementación de Eclipse MicroProfile Config 3.1 para JettraEE.
 * Orden de precedencia según especificación:
 * 1. System Properties (ordinal 400)
 * 2. Variables de Entorno (ordinal 300)
 * 3. META-INF/microprofile-config.properties (ordinal 100)
 * 4. application.properties / jettra.properties (ordinal 90)
 */
public class JettraMPConfig implements Config {

    private final List<ConfigSource> configSources = new ArrayList<>();
    private final Map<Class<?>, Converter<?>> converters = new HashMap<>();

    public JettraMPConfig() {
        this(Thread.currentThread().getContextClassLoader() != null
                ? Thread.currentThread().getContextClassLoader()
                : JettraMPConfig.class.getClassLoader());
    }

    public JettraMPConfig(ClassLoader classLoader) {
        initDefaultSources(classLoader);
        initDefaultConverters();
    }

    private void initDefaultSources(ClassLoader cl) {
        // 1. System Properties (400)
        configSources.add(new ConfigSource() {
            @Override
            public int getOrdinal() { return 400; }
            @Override
            public String getName() { return "SystemPropertiesConfigSource"; }
            @Override
            public Set<String> getPropertyNames() { return System.getProperties().stringPropertyNames(); }
            @Override
            public String getValue(String propertyName) { return System.getProperty(propertyName); }
        });

        // 2. Environment Variables (300)
        configSources.add(new ConfigSource() {
            @Override
            public int getOrdinal() { return 300; }
            @Override
            public String getName() { return "EnvVariablesConfigSource"; }
            @Override
            public Set<String> getPropertyNames() { return System.getenv().keySet(); }
            @Override
            public String getValue(String propertyName) {
                String val = System.getenv(propertyName);
                if (val == null) {
                    val = System.getenv(propertyName.toUpperCase().replace('.', '_').replace('-', '_'));
                }
                return val;
            }
        });

        // 3. META-INF/microprofile-config.properties (100)
        loadPropertiesResource(cl, "META-INF/microprofile-config.properties", "MicroProfilePropertiesConfigSource", 100);

        // 3b. WEB-INF/microprofile-config.properties (100)
        loadPropertiesResource(cl, "WEB-INF/microprofile-config.properties", "WebInfMicroProfileConfigSource", 100);

        // 4. application.properties (90)
        loadPropertiesResource(cl, "application.properties", "ApplicationPropertiesConfigSource", 90);

        // 5. jettra.properties (85)
        loadPropertiesResource(cl, "jettra.properties", "JettraPropertiesConfigSource", 85);

        // Ordenar por ordinal descendente
        configSources.sort((a, b) -> Integer.compare(b.getOrdinal(), a.getOrdinal()));
    }

    private void loadPropertiesResource(ClassLoader cl, String resourcePath, String sourceName, int ordinal) {
        InputStream is = cl.getResourceAsStream(resourcePath);
        if (is == null) {
            String[] fileCandidates = new String[]{
                    resourcePath,
                    "src/main/resources/" + resourcePath,
                    "src/main/webapp/" + resourcePath,
                    "src/main/webapp/WEB-INF/" + resourcePath
            };
            for (String fc : fileCandidates) {
                java.io.File f = new java.io.File(fc);
                if (f.exists() && !f.isDirectory()) {
                    try {
                        is = new java.io.FileInputStream(f);
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (is != null) {
            try (InputStream stream = is) {
                Properties props = new Properties();
                props.load(stream);
                configSources.add(new ConfigSource() {
                    @Override
                    public int getOrdinal() { return ordinal; }
                    @Override
                    public String getName() { return sourceName; }
                    @Override
                    public Set<String> getPropertyNames() { return props.stringPropertyNames(); }
                    @Override
                    public String getValue(String propertyName) { return props.getProperty(propertyName); }
                });
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void initDefaultConverters() {
        converters.put(String.class, (Converter<String>) value -> value);
        converters.put(Integer.class, (Converter<Integer>) Integer::parseInt);
        converters.put(int.class, (Converter<Integer>) Integer::parseInt);
        converters.put(Long.class, (Converter<Long>) Long::parseLong);
        converters.put(long.class, (Converter<Long>) Long::parseLong);
        converters.put(Double.class, (Converter<Double>) Double::parseDouble);
        converters.put(double.class, (Converter<Double>) Double::parseDouble);
        converters.put(Float.class, (Converter<Float>) Float::parseFloat);
        converters.put(float.class, (Converter<Float>) Float::parseFloat);
        converters.put(Boolean.class, (Converter<Boolean>) Boolean::parseBoolean);
        converters.put(boolean.class, (Converter<Boolean>) Boolean::parseBoolean);
    }

    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        return getOptionalValue(propertyName, propertyType)
                .orElseThrow(() -> new NoSuchElementException("Property not found: " + propertyName));
    }

    @Override
    public ConfigValue getConfigValue(String propertyName) {
        for (ConfigSource cs : configSources) {
            String val = cs.getValue(propertyName);
            if (val != null) {
                return new ConfigValue() {
                    @Override public String getName() { return propertyName; }
                    @Override public String getValue() { return val; }
                    @Override public String getRawValue() { return val; }
                    @Override public String getSourceName() { return cs.getName(); }
                    @Override public int getSourceOrdinal() { return cs.getOrdinal(); }
                };
            }
        }
        return new ConfigValue() {
            @Override public String getName() { return propertyName; }
            @Override public String getValue() { return null; }
            @Override public String getRawValue() { return null; }
            @Override public String getSourceName() { return null; }
            @Override public int getSourceOrdinal() { return 0; }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        for (ConfigSource cs : configSources) {
            String raw = cs.getValue(propertyName);
            if (raw != null) {
                Converter<T> converter = (Converter<T>) converters.get(propertyType);
                if (converter != null) {
                    return Optional.ofNullable(converter.convert(raw));
                }
                if (propertyType.isEnum()) {
                    T enumVal = (T) Enum.valueOf((Class<Enum>) propertyType, raw);
                    return Optional.of(enumVal);
                }
                return Optional.of((T) raw);
            }
        }
        return Optional.empty();
    }

    @Override
    public Iterable<String> getPropertyNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ConfigSource cs : configSources) {
            names.addAll(cs.getPropertyNames());
        }
        return names;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        return Collections.unmodifiableList(configSources);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
        return Optional.ofNullable((Converter<T>) converters.get(forType));
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap to: " + type.getName());
    }

    public void addConfigSource(ConfigSource source) {
        configSources.add(source);
        configSources.sort((a, b) -> Integer.compare(b.getOrdinal(), a.getOrdinal()));
    }
}
