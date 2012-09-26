
package org.codejive.rws;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.codejive.rws.events.MulticastEvent;
import org.codejive.rws.events.MulticastListener;
import org.codejive.rws.events.SessionEvent;
import org.codejive.rws.events.SessionListener;
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
    private final Set<SessionListener> sessionListeners = new CopyOnWriteArraySet<SessionListener>();
    private final Set<MulticastListener> multicastListeners = new CopyOnWriteArraySet<MulticastListener>();
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

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public Set<String> listAttributeNames() {
        return Collections.unmodifiableSet(attributes.keySet());
    }

    public void clearAttributes() {
        attributes.clear();
    }

    // ---------------------------------------------------------------------
    // SESSION EVENT HANDLING
    // ---------------------------------------------------------------------

    public RwsSession addSession(RwsWebSocketAdapter adapter) {
        RwsSession session = new RwsSession(this, adapter);
        sessions.put(session.getId(), session);
        fireConnect(session);
        return session;
    }

    public void removeSession(RwsSession session) {
        sessions.remove(session.getId());
        session.clearAttributes();
        fireDisconnect(session);
    }

    public Collection<RwsSession> listSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void addSessionListener(SessionListener listener) {
        sessionListeners.add(listener);
    }

    public void removeSessionListener(SessionListener listener) {
        sessionListeners.remove(listener);
    }

    private void fireConnect(RwsSession session) {
        SessionEvent event = new SessionEvent(session);
        for (SessionListener l : sessionListeners) {
            try {
                l.connect(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    private void fireDisconnect(RwsSession session) {
        SessionEvent event = new SessionEvent(session);
        for (SessionListener l : sessionListeners) {
            try {
                l.disconnect(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    protected void fireChange(RwsSession session) {
        SessionEvent event = new SessionEvent(session);
        for (SessionListener l : sessionListeners) {
            try {
                l.change(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    // ---------------------------------------------------------------------
    // MULTICAST EVENT HANDLING
    // ---------------------------------------------------------------------

    public Collection<String> listMulticastGroups() {
        HashSet<String> groups = new HashSet<String>();
        for (RwsSession session : sessions.values()) {
            groups.addAll(session.listMulticastGroups());
        }
        return Collections.unmodifiableCollection(groups);
    }

    public Collection<RwsSession> listMulticastMembers(String group) {
        HashSet<RwsSession> members = new HashSet<RwsSession>();
        for (RwsSession session : sessions.values()) {
            Collection<String> groups = session.listMulticastGroups();
            if (groups.contains(group)) {
                members.add(session);
            }
        }
        return Collections.unmodifiableCollection(members);
    }

    public void addMulticastListener(MulticastListener listener) {
        multicastListeners.add(listener);
    }

    public void removeMulticastListener(MulticastListener listener) {
        multicastListeners.remove(listener);
    }

    protected void fireJoin(String group, RwsSession session) {
        MulticastEvent event = new MulticastEvent(group, session);
        for (MulticastListener l : multicastListeners) {
            try {
                l.join(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    protected void fireLeave(String group, RwsSession session) {
        MulticastEvent event = new MulticastEvent(group, session);
        for (MulticastListener l : multicastListeners) {
            try {
                l.leave(event);
            } catch (Throwable th) {
                log.warn("Could not fire event on a listener");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Communication
    // ---------------------------------------------------------------------

    public void sendTo(String from, String to, JSONObject data) throws IOException {
        RwsSession session = sessions.get(to);
        if (session != null) {
            send(session, from, data);
        }
    }

    public void sendAll(String from, JSONObject data, boolean meToo) {
        for (RwsSession session : sessions.values()) {
            if (meToo || !session.getId().equals(from)) {
                try {
                    send(session, from, data);
                } catch (IOException ex) {
                    // Ignore
                }
            }
        }
    }

    public void sendMulti(String from, String group, JSONObject data, boolean meToo) {
        Collection<RwsSession> members = listMulticastMembers(group);
        for (RwsSession session : members) {
            if (meToo || !session.getId().equals(from)) {
                try {
                    send(session, from, data);
                } catch (IOException ex) {
                    // Ignore
                }
            }
        }
    }

    private void send(RwsSession session, String from, JSONObject data) throws IOException {
        try {
            session.send(from, data);
        } catch (IOException ex) {
            log.error("Could not send message, disconnecting socket", ex);
            removeSession(session);
            if (session.isConnected()) {
                session.disconnect();
            }
            throw ex;
        }
    }
}
