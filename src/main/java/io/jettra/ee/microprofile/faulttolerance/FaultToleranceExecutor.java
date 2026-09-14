package io.jettra.ee.microprofile.faulttolerance;

import io.jettra.ee.core.IO;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

/**
 * Motor de MicroProfile Fault Tolerance 4.0 para JettraEE.
 * Aplica resiliencia mediante @Retry, @Timeout y @Fallback usando Virtual Threads.
 */
public class FaultToleranceExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public static Object execute(Object target, Method method, Object[] args) throws Throwable {
        boolean hasRetry = method.isAnnotationPresent(Retry.class);
        boolean hasTimeout = method.isAnnotationPresent(Timeout.class);
        boolean hasFallback = method.isAnnotationPresent(Fallback.class);

        int maxRetries = 0;
        long delayMillis = 0;
        Class<? extends Throwable>[] retryOn = null;

        if (hasRetry) {
            Retry r = method.getAnnotation(Retry.class);
            maxRetries = r.maxRetries();
            delayMillis = r.delayUnit().getDuration().toMillis() * r.delay();
            retryOn = r.retryOn();
        }

        int attempts = 0;
        Throwable lastException = null;

        while (attempts <= maxRetries) {
            attempts++;
            try {
                if (hasTimeout) {
                    Timeout to = method.getAnnotation(Timeout.class);
                    long timeoutMillis = to.unit().getDuration().toMillis() * to.value();
                    Future<Object> future = VIRTUAL_EXECUTOR.submit(() -> {
                        method.setAccessible(true);
                        return method.invoke(target, args);
                    });
                    try {
                        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException te) {
                        future.cancel(true);
                        throw new TimeoutException("La operación excedió el tiempo límite de " + timeoutMillis + "ms", te);
                    } catch (ExecutionException ee) {
                        throw ee.getCause() != null ? ee.getCause() : ee;
                    }
                } else {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                }
            } catch (Throwable t) {
                lastException = (t instanceof java.lang.reflect.InvocationTargetException ite) ? ite.getCause() : t;
                boolean shouldRetry = false;
                if (hasRetry && attempts <= maxRetries) {
                    if (retryOn != null && retryOn.length > 0) {
                        for (Class<? extends Throwable> expClass : retryOn) {
                            if (expClass.isAssignableFrom(lastException.getClass())) {
                                shouldRetry = true;
                                break;
                            }
                        }
                    } else {
                        shouldRetry = true;
                    }
                }

                if (shouldRetry) {
                    IO.warn("Reintentando método " + method.getName() + " (intento " + attempts + "/" + maxRetries + "): " + lastException.getMessage());
                    if (delayMillis > 0) {
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    continue;
                }
                break;
            }
        }

        // Manejar Fallback si falló
        if (hasFallback && lastException != null) {
            Fallback fb = method.getAnnotation(Fallback.class);
            String fallbackMethodName = fb.fallbackMethod();

            if (!fallbackMethodName.isEmpty()) {
                try {
                    Method fbMethod = target.getClass().getMethod(fallbackMethodName, method.getParameterTypes());
                    fbMethod.setAccessible(true);
                    IO.info("Ejecutando fallbackMethod '" + fallbackMethodName + "' tras fallo en " + method.getName());
                    return fbMethod.invoke(target, args);
                } catch (Exception e) {
                    IO.error("Error al invocar fallbackMethod '" + fallbackMethodName + "'", e);
                }
            } else if (fb.value() != Fallback.DEFAULT.class) {
                try {
                    FallbackHandler<?> handler = fb.value().getDeclaredConstructor().newInstance();
                    IO.info("Ejecutando FallbackHandler '" + fb.value().getSimpleName() + "' tras fallo en " + method.getName());
                    final Throwable finalFailure = lastException;
                    return handler.handle(new org.eclipse.microprofile.faulttolerance.ExecutionContext() {
                        @Override public Method getMethod() { return method; }
                        @Override public Object[] getParameters() { return args; }
                        @Override public Throwable getFailure() { return finalFailure; }
                    });
                } catch (Exception e) {
                    IO.error("Error al invocar FallbackHandler", e);
                }
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return null;
    }
}
