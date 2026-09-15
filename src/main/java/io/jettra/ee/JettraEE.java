package io.jettra.ee;

import io.jettra.ee.core.ClassScanner;
import io.jettra.ee.core.IO;
import io.jettra.ee.integration.FluxIntegration;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.ee.jakarta.rest.RestDispatcher;
import io.jettra.ee.microprofile.config.JettraMPConfig;
import io.jettra.ee.microprofile.health.HealthEndpointHandler;
import io.jettra.ee.microprofile.metrics.MetricsEndpointHandler;
import io.jettra.ee.microprofile.openapi.OpenApiEndpointHandler;
import io.jettra.ee.microprofile.restclient.JettraRestClientBuilder;
import io.jettra.ee.server.JettraEEServer;
import io.jettra.ee.server.WebResourceManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.net.URI;
import java.util.*;

/**
 * Fachada principal y Builder moderno para JettraEE.
 * Permite iniciar el servidor con autodescubrimiento o configuración fluida programática.
 */
public class JettraEE {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    public static void main(String[] args) {
        printBanner();
        int port = 8080;
        String contextPath = "/";
        String webappRoot = null;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                } else if ("--context-path".equals(args[i]) && i + 1 < args.length) {
                    contextPath = args[++i];
                } else if ("--webapp-root".equals(args[i]) && i + 1 < args.length) {
                    webappRoot = args[++i];
                }
            }
        }

        // Si se encuentra una propiedad en microprofile-config o application.properties
        try {
            var cfg = ConfigProvider.getConfig();
            port = cfg.getOptionalValue("server.port", Integer.class).orElse(port);
            contextPath = cfg.getOptionalValue("server.contextpath", String.class).orElse(contextPath);
            webappRoot = cfg.getOptionalValue("server.webapp.root", String.class).orElse(webappRoot);
        } catch (Throwable ignored) {}

        JettraEE.builder()
                .port(port)
                .contextPath(contextPath)
                .webappRoot(webappRoot)
                .scanClasspath()
                .build()
                .start();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Inicia JettraEE escaneando el paquete de la clase principal indicada.
     */
    public static JettraEEServer start(Class<?> appClass, String[] args) {
        printBanner();
        String basePackage = appClass.getPackageName();

        int port = 8080;
        String contextPath = "/";
        String webappRoot = null;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                } else if ("--context-path".equals(args[i]) && i + 1 < args.length) {
                    contextPath = args[++i];
                } else if ("--webapp-root".equals(args[i]) && i + 1 < args.length) {
                    webappRoot = args[++i];
                }
            }
        }

        try {
            var cfg = ConfigProvider.getConfig();
            port = cfg.getOptionalValue("server.port", Integer.class).orElse(port);
            contextPath = cfg.getOptionalValue("server.contextpath", String.class).orElse(contextPath);
            webappRoot = cfg.getOptionalValue("server.webapp.root", String.class).orElse(webappRoot);
        } catch (Throwable ignored) {}

        JettraEEServer server = builder()
                .port(port)
                .contextPath(contextPath)
                .webappRoot(webappRoot)
                .registerOpenApiClass(appClass)
                .scanPackages(basePackage)
                .build();

        server.start();
        return server;
    }

    public static void printBanner() {
        System.out.println(IO.CYAN + IO.BOLD);
        System.out.println("     _      _   _             _____ _____ ");
        System.out.println("    | |    | | | |           |  ___|  ___|");
        System.out.println("    | | ___| |_| |_ _ __ __ _| |__ | |__  ");
        System.out.println(" _  | |/ _ \\ __| __| '__/ _` |  __||  __| ");
        System.out.println("| |_| |  __/ |_| |_| | | (_| | |___| |___ ");
        System.out.println(" \\___/ \\___|\\__|\\__|_|  \\__,_\\____/\\____/ ");
        System.out.println(IO.RESET);
        System.out.println(IO.GREEN + " >> Eclipse MicroProfile & Jakarta EE 11/12 Server [v" + VERSION + "]" + IO.RESET);
        System.out.println(IO.PURPLE + " >> Built-in JettraFlux & JettraRules Native Support" + IO.RESET);
        System.out.println(IO.YELLOW + " >> Java 25 Virtual Threads Engine (Project Loom)" + IO.RESET);
        System.out.println();
    }

    public static class Builder {
        private int port = 8080;
        private String contextPath = "/";
        private String webappRoot = null;
        private final List<String> scanPackages = new ArrayList<>();
        private final List<Class<?>> manualResources = new ArrayList<>();
        private final List<Class<? extends HealthCheck>> manualHealthChecks = new ArrayList<>();
        private final Map<String, Class<?>> manualFluxPages = new LinkedHashMap<>();
        private String appTitle = "JettraEE API";
        private String appVersion = "1.0.0";

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public Builder webappRoot(String webappRoot) {
            this.webappRoot = webappRoot;
            return this;
        }

        public Builder title(String title) {
            this.appTitle = title;
            return this;
        }

        public Builder version(String version) {
            this.appVersion = version;
            return this;
        }

        public Builder scanPackages(String... packages) {
            if (packages != null) {
                this.scanPackages.addAll(Arrays.asList(packages));
            }
            return this;
        }

        public Builder scanClasspath() {
            this.scanPackages.add("");
            return this;
        }

        public Builder registerResource(Class<?> resourceClass) {
            this.manualResources.add(resourceClass);
            return this;
        }

        public Builder registerHealthCheck(Class<? extends HealthCheck> checkClass) {
            this.manualHealthChecks.add(checkClass);
            return this;
        }

        private final List<Class<?>> manualOpenApiClasses = new ArrayList<>();

        public Builder registerOpenApiClass(Class<?> clazz) {
            if (clazz != null) {
                this.manualOpenApiClasses.add(clazz);
            }
            return this;
        }

        public Builder registerFluxPage(String path, Class<?> pageClass) {
            this.manualFluxPages.put(path, pageClass);
            return this;
        }

        public JettraEEServer build() {
            // Inicializar CDI
            JettraCDIContainer.initialize();

            RestDispatcher restDispatcher = new RestDispatcher(contextPath);
            HealthEndpointHandler healthHandler = new HealthEndpointHandler();
            MetricsEndpointHandler metricsHandler = new MetricsEndpointHandler();
            OpenApiEndpointHandler openApiHandler = new OpenApiEndpointHandler(restDispatcher, appTitle, appVersion);
            FluxIntegration fluxIntegration = new FluxIntegration();

            // 0. Inicializar Subsistema JettraSecurityDB y registrar en CDI y REST
            try {
                io.jettra.ee.security.repository.JettraSecurityDBInitializer.initializeIfEmpty();
            } catch (Throwable t) {
                IO.warn("No se pudo auto-inicializar JettraSecurityDB: " + t.getMessage());
            }

            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JRoleRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JUserRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JCredentialRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JAccreditationRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.service.JettraSecurityService.class);

            restDispatcher.registerResource(io.jettra.ee.security.rest.SecurityAuthController.class);
            restDispatcher.registerResource(io.jettra.ee.security.rest.SecurityUserController.class);
            openApiHandler.registerScannedClass(io.jettra.ee.security.rest.SecurityAuthController.class);
            openApiHandler.registerScannedClass(io.jettra.ee.security.rest.SecurityUserController.class);

            // Autodescubrimiento si hay paquetes definidos
            if (!scanPackages.isEmpty()) {
                ClassScanner scanner = new ClassScanner(scanPackages);

                // 1. Descubrir recursos Jakarta REST (@Path)
                Set<Class<?>> restClasses = scanner.findAnnotatedClasses(Path.class);
                for (Class<?> rc : restClasses) {
                    restDispatcher.registerResource(rc);
                }

                // 2. Descubrir beans CDI
                Set<Class<?>> appScoped = scanner.findAnnotatedClasses(ApplicationScoped.class);
                Set<Class<?>> reqScoped = scanner.findAnnotatedClasses(RequestScoped.class);
                Set<Class<?>> singletons = scanner.findAnnotatedClasses(Singleton.class);
                for (Class<?> bc : appScoped) JettraCDIContainer.getInstance().registerBean(bc);
                for (Class<?> bc : reqScoped) JettraCDIContainer.getInstance().registerBean(bc);
                for (Class<?> bc : singletons) JettraCDIContainer.getInstance().registerBean(bc);

                // 3. Descubrir HealthChecks
                Set<Class<?>> liveness = scanner.findAnnotatedClasses(Liveness.class);
                Set<Class<?>> readiness = scanner.findAnnotatedClasses(Readiness.class);
                Set<Class<?>> startup = scanner.findAnnotatedClasses(Startup.class);
                for (Class<?> hc : liveness) {
                    if (HealthCheck.class.isAssignableFrom(hc)) healthHandler.registerCheck((Class<? extends HealthCheck>) hc);
                }
                for (Class<?> hc : readiness) {
                    if (HealthCheck.class.isAssignableFrom(hc)) healthHandler.registerCheck((Class<? extends HealthCheck>) hc);
                }
                for (Class<?> hc : startup) {
                    if (HealthCheck.class.isAssignableFrom(hc)) healthHandler.registerCheck((Class<? extends HealthCheck>) hc);
                }

                // 4. Descubrir Rest Clients (@RegisterRestClient)
                Set<Class<?>> restClients = scanner.findAnnotatedClasses(RegisterRestClient.class);
                for (Class<?> clientInterface : restClients) {
                    try {
                        RegisterRestClient ann = clientInterface.getAnnotation(RegisterRestClient.class);
                        String uriStr = ann.baseUri();
                        if (!uriStr.isEmpty()) {
                            Object proxy = new JettraRestClientBuilder().baseUri(URI.create(uriStr)).build(clientInterface);
                            JettraCDIContainer.getInstance().registerSingleton((Class<Object>) clientInterface, proxy);
                            IO.info("MicroProfile RestClient registrado: " + clientInterface.getSimpleName() + " -> " + uriStr);
                        }
                    } catch (Exception e) {
                        IO.warn("No se pudo auto-registrar RestClient " + clientInterface.getSimpleName() + ": " + e.getMessage());
                    }
                }

                // 5. Descubrir Páginas JettraFlux
                Set<Class<? extends io.jettra.flux.pages.FluxBaseHandler>> fluxPages = scanner.findSubclassesOf(io.jettra.flux.pages.FluxBaseHandler.class);
                for (Class<?> fp : fluxPages) {
                    if (fp.isAnnotationPresent(io.jettra.core.server.Page.class)) {
                        io.jettra.core.server.Page pageAnn = fp.getAnnotation(io.jettra.core.server.Page.class);
                        fluxIntegration.registerPage(pageAnn.path(), fp);
                    }
                }

                // 6. Descubrir anotaciones MicroProfile OpenAPI (@OpenAPIDefinition, @SecurityScheme, @SecuritySchemes)
                Set<Class<?>> openApiDefs = scanner.findAnnotatedClasses(org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition.class);
                openApiHandler.registerScannedClasses(openApiDefs);
                Set<Class<?>> secSchemeClasses = scanner.findAnnotatedClasses(org.eclipse.microprofile.openapi.annotations.security.SecurityScheme.class);
                openApiHandler.registerScannedClasses(secSchemeClasses);
                Set<Class<?>> secSchemesClasses = scanner.findAnnotatedClasses(org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes.class);
                openApiHandler.registerScannedClasses(secSchemesClasses);
            }

            // Registros manuales adicionales
            for (Class<?> r : manualResources) {
                restDispatcher.registerResource(r);
            }
            for (Class<? extends HealthCheck> hc : manualHealthChecks) {
                healthHandler.registerCheck(hc);
            }
            for (Map.Entry<String, Class<?>> entry : manualFluxPages.entrySet()) {
                fluxIntegration.registerPage(entry.getKey(), entry.getValue());
            }
            for (Class<?> c : manualOpenApiClasses) {
                openApiHandler.registerScannedClass(c);
            }

            WebResourceManager webResourceManager = new WebResourceManager(webappRoot);
            return new JettraEEServer(port, contextPath, restDispatcher, healthHandler, metricsHandler, openApiHandler, fluxIntegration, webResourceManager);
        }
    }
}
