package io.jettra.ee.microprofile.restclient;

import io.jettra.ee.core.IO;
import io.jettra.json.JettraJson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.ext.QueryParamStyle;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementación de RestClientBuilder para MicroProfile Rest Client 3.0 en JettraEE.
 * Emplea java.net.http.HttpClient nativo con Virtual Threads.
 */
public class JettraRestClientBuilder implements RestClientBuilder {

    private URI baseUri;
    private long connectTimeoutMillis = 10000;
    private long readTimeoutMillis = 30000;
    private final JettraJson json = new JettraJson();

    @Override
    public RestClientBuilder baseUrl(URL url) {
        try {
            this.baseUri = url.toURI();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    @Override
    public RestClientBuilder baseUri(URI uri) {
        this.baseUri = uri;
        return this;
    }

    @Override
    public RestClientBuilder connectTimeout(long timeout, TimeUnit timeUnit) {
        this.connectTimeoutMillis = timeUnit.toMillis(timeout);
        return this;
    }

    @Override
    public RestClientBuilder readTimeout(long timeout, TimeUnit timeUnit) {
        this.readTimeoutMillis = timeUnit.toMillis(timeout);
        return this;
    }

    @Override
    public RestClientBuilder executorService(ExecutorService executor) {
        return this;
    }

    @Override
    public RestClientBuilder sslContext(SSLContext sslContext) {
        return this;
    }

    @Override
    public RestClientBuilder trustStore(KeyStore trustStore) {
        return this;
    }

    @Override
    public RestClientBuilder keyStore(KeyStore keyStore, String keystorePassword) {
        return this;
    }

    @Override
    public RestClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        return this;
    }

    @Override
    public RestClientBuilder followRedirects(boolean follow) {
        return this;
    }

    @Override
    public RestClientBuilder proxyAddress(String proxyHost, int proxyPort) {
        return this;
    }

    @Override
    public RestClientBuilder queryParamStyle(QueryParamStyle style) {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> aClass) throws IllegalStateException, RestClientDefinitionException {
        if (baseUri == null) {
            throw new IllegalStateException("baseUri o baseUrl debe ser especificado antes de invocar build()");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        return (T) Proxy.newProxyInstance(
                aClass.getClassLoader(),
                new Class<?>[]{aClass},
                (proxy, method, args) -> invokeClientMethod(client, aClass, method, args)
        );
    }

    private Object invokeClientMethod(HttpClient client, Class<?> clientInterface, Method method, Object[] args) throws Exception {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // Obtener HTTP Method
        String httpMethod = "GET";
        if (method.isAnnotationPresent(POST.class)) httpMethod = "POST";
        else if (method.isAnnotationPresent(PUT.class)) httpMethod = "PUT";
        else if (method.isAnnotationPresent(DELETE.class)) httpMethod = "DELETE";
        else if (method.isAnnotationPresent(PATCH.class)) httpMethod = "PATCH";

        // Obtener Path
        String classPath = clientInterface.isAnnotationPresent(Path.class) ? clientInterface.getAnnotation(Path.class).value() : "";
        String methodPath = method.isAnnotationPresent(Path.class) ? method.getAnnotation(Path.class).value() : "";

        if (!classPath.startsWith("/") && !classPath.isEmpty()) classPath = "/" + classPath;
        if (!methodPath.startsWith("/") && !methodPath.isEmpty()) methodPath = "/" + methodPath;
        String fullPath = classPath + methodPath;

        StringBuilder query = new StringBuilder();
        Object bodyPayload = null;

        Parameter[] params = method.getParameters();
        if (params != null && args != null) {
            for (int i = 0; i < params.length; i++) {
                Parameter p = params[i];
                Object argVal = args[i];

                if (p.isAnnotationPresent(PathParam.class)) {
                    String pName = p.getAnnotation(PathParam.class).value();
                    String strVal = argVal != null ? URLEncoder.encode(argVal.toString(), StandardCharsets.UTF_8) : "";
                    fullPath = fullPath.replace("{" + pName + "}", strVal);
                } else if (p.isAnnotationPresent(QueryParam.class)) {
                    String qName = p.getAnnotation(QueryParam.class).value();
                    if (argVal != null) {
                        if (query.length() > 0) query.append("&");
                        query.append(URLEncoder.encode(qName, StandardCharsets.UTF_8))
                             .append("=")
                             .append(URLEncoder.encode(argVal.toString(), StandardCharsets.UTF_8));
                    }
                } else {
                    bodyPayload = argVal;
                }
            }
        }

        String base = baseUri.toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String targetUrl = base + fullPath;
        if (query.length() > 0) {
            targetUrl += "?" + query;
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMillis(readTimeoutMillis))
                .header("Accept", MediaType.APPLICATION_JSON);

        if (bodyPayload != null) {
            reqBuilder.header("Content-Type", MediaType.APPLICATION_JSON);
            String jsonBody = (bodyPayload instanceof String s) ? s : json.toJson(bodyPayload);
            reqBuilder.method(httpMethod, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            if ("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
                reqBuilder.method(httpMethod, HttpRequest.BodyPublishers.noBody());
            } else {
                reqBuilder.method(httpMethod, HttpRequest.BodyPublishers.noBody());
            }
        }

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }

        if (returnType == String.class) {
            return response.body();
        }

        return json.fromJson(response.body(), returnType);
    }

    // --- Configurable ---
    @Override public Configuration getConfiguration() { return null; }
    @Override public RestClientBuilder property(String name, Object value) { return this; }
    @Override public RestClientBuilder register(Class<?> componentClass) { return this; }
    @Override public RestClientBuilder register(Class<?> componentClass, int priority) { return this; }
    @Override public RestClientBuilder register(Class<?> componentClass, Class<?>... contracts) { return this; }
    @Override public RestClientBuilder register(Class<?> componentClass, Map<Class<?>, Integer> contracts) { return this; }
    @Override public RestClientBuilder register(Object component) { return this; }
    @Override public RestClientBuilder register(Object component, int priority) { return this; }
    @Override public RestClientBuilder register(Object component, Class<?>... contracts) { return this; }
    @Override public RestClientBuilder register(Object component, Map<Class<?>, Integer> contracts) { return this; }
}
