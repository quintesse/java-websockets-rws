
package org.codejive.rws;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsContext {
    private final RwsRegistry registry = new RwsRegistry();
    private final Map<String, RwsSession> sessions = new ConcurrentHashMap<String, RwsSession>();
    private final Set<SessionListener> listeners = new CopyOnWriteArraySet<SessionListener>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

    private final Logger log = LoggerFactory.getLogger(RwsContext.class);

    public RwsRegistry getRegistry() {
        return registry;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(attributes.keySet());
    }

    public void clearAttributes() {
        attributes.clear();
    }

    public class SessionEvent extends EventObject {
        public SessionEvent(RwsSession client) {
            super(client);
        }
    }

    public interface SessionListener extends EventListener {
        void connect(SessionEvent event);
        void disconnect(SessionEvent event);
        void change(SessionEvent event);
    }

    public RwsSession addSession(RwsWebSocketAdapter adapter) {
        RwsSession client = new RwsSession(this, adapter);
        sessions.put(client.getId(), client);
        fireConnect(client);
        return client;
    }

    public void removeSession(RwsSession client) {
        sessions.remove(client.getId());
        client.clearAttributes();
        fireDisconnect(client);
    }

    public Collection<RwsSession> listSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void sendTo(String from, String to, JSONObject data) throws IOException {
        RwsSession client = sessions.get(to);
        if (client != null) {
            send(client, from, data);
        }
    }

    public void sendAll(String from, JSONObject data, boolean meToo) {
        for (RwsSession client : sessions.values()) {
            if (meToo || !client.getId().equals(from)) {
                try {
                    send(client, from, data);
                } catch (IOException ex) {
                    // Ignore
                }
            }
        }
    }

    private void send(RwsSession client, String from, JSONObject data) throws IOException {
        try {
            client.send(from, data);
        } catch (IOException ex) {
            log.error("Could not send message, disconnecting socket", ex);
            removeSession(client);
            if (client.isConnected()) {
                client.disconnect();
            }
            throw ex;
        }
    }

    public void addSessionListener(SessionListener listener) {
        listeners.add(listener);
    }

    public void removeSessionListener(SessionListener listener) {
        listeners.remove(listener);
    }

    private void fireConnect(RwsSession client) {
        SessionEvent event = new SessionEvent(client);
        for (SessionListener l : listeners) {
            try {
                l.connect(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    private void fireDisconnect(RwsSession client) {
        SessionEvent event = new SessionEvent(client);
        for (SessionListener l : listeners) {
            try {
                l.disconnect(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    protected void fireChange(RwsSession client) {
        SessionEvent event = new SessionEvent(client);
        for (SessionListener l : listeners) {
            try {
                l.change(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }
}
