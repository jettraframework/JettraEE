package io.jettra.server.core;

import io.jettra.flux.core.FluxContext;
import java.util.Map;

/**
 * Contexto y gestión de ámbitos (Request, Session, Application) para JettraEE.
 * Hereda de FluxContext para ofrecer 100% de interoperabilidad y compatibilidad
 * con páginas y widgets de JettraFlux, compartiendo el mismo almacén de estados y sesiones.
 */
public class JettraContext extends FluxContext {

    private final FluxContext delegate;

    public JettraContext(String sessionId) {
        super(sessionId);
        this.delegate = null;
    }

    protected JettraContext(FluxContext delegate) {
        super(delegate.getSessionId());
        this.delegate = delegate;
    }

    public static void setCurrent(FluxContext context) {
        FluxContext.setCurrent(context);
    }

    public static JettraContext getCurrent() {
        FluxContext cur = FluxContext.getCurrent();
        if (cur == null) {
            return null;
        }
        if (cur instanceof JettraContext jc) {
            return jc;
        }
        return new JettraContext(cur);
    }

    @Override
    public Object get(Scope scope, String key) {
        if (delegate != null) {
            return delegate.get(scope, key);
        }
        return super.get(scope, key);
    }

    @Override
    public void set(Scope scope, String key, Object value) {
        if (delegate != null) {
            delegate.set(scope, key, value);
        } else {
            super.set(scope, key, value);
        }
    }

    @Override
    public void destroyRequest() {
        if (delegate != null) {
            delegate.destroyRequest();
        } else {
            super.destroyRequest();
        }
    }

    @Override
    public void destroyView() {
        if (delegate != null) {
            delegate.destroyView();
        } else {
            super.destroyView();
        }
    }

    public static void destroySession(String sid) {
        Map<String, Object> sessMap = sessions.remove(sid);
        if (sessMap != null) {
            sessMap.clear();
        }
    }

    public static void clearSessions() {
        sessions.clear();
    }

    public static int getActiveSessionCount() {
        return sessions.size();
    }
}
