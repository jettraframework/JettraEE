package io.jettra.ee.microprofile.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolver SPI para MicroProfile Config en JettraEE.
 */
public class JettraConfigProviderResolver extends ConfigProviderResolver {

    private static final Map<ClassLoader, Config> configs = new ConcurrentHashMap<>();
    private static volatile Config defaultConfig;

    @Override
    public Config getConfig() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = JettraConfigProviderResolver.class.getClassLoader();
        }
        return getConfig(cl);
    }

    @Override
    public Config getConfig(ClassLoader loader) {
        return configs.computeIfAbsent(loader, l -> {
            if (defaultConfig != null) {
                return defaultConfig;
            }
            return new JettraMPConfig(l);
        });
    }

    @Override
    public ConfigBuilder getBuilder() {
        return new JettraConfigBuilder();
    }

    @Override
    public void registerConfig(Config config, ClassLoader classLoader) {
        configs.put(classLoader, config);
        if (defaultConfig == null) {
            defaultConfig = config;
        }
    }

    @Override
    public void releaseConfig(Config config) {
        configs.values().remove(config);
        if (defaultConfig == config) {
            defaultConfig = null;
        }
    }

    public static class JettraConfigBuilder implements ConfigBuilder {

        private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        private final List<ConfigSource> customSources = new ArrayList<>();

        @Override
        public ConfigBuilder addDefaultSources() {
            return this;
        }

        @Override
        public ConfigBuilder addDiscoveredSources() {
            return this;
        }

        @Override
        public ConfigBuilder addDiscoveredConverters() {
            return this;
        }

        @Override
        public ConfigBuilder forClassLoader(ClassLoader loader) {
            this.classLoader = loader;
            return this;
        }

        @Override
        public ConfigBuilder withSources(ConfigSource... sources) {
            if (sources != null) {
                for (ConfigSource s : sources) {
                    customSources.add(s);
                }
            }
            return this;
        }

        @Override
        public ConfigBuilder withConverters(Converter<?>... converters) {
            return this;
        }

        @Override
        public <T> ConfigBuilder withConverter(Class<T> type, int priority, Converter<T> converter) {
            return this;
        }

        @Override
        public Config build() {
            JettraMPConfig cfg = new JettraMPConfig(classLoader);
            for (ConfigSource cs : customSources) {
                cfg.addConfigSource(cs);
            }
            return cfg;
        }
    }
}
