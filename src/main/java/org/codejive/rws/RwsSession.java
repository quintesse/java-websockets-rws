package org.codejive.rws;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsSession {
    private final RwsContext context;
    private RwsWebSocketAdapter adapter;
    private final String id;
    private String name;

    private final HashMap<String, Object> attributes;
    private final HashMap<String, Subscription> subscriptions;
    private final HashMap<String, EventListener> listeners;
    private final HashSet<String> groups;

    private static long nextSessionId = 1;

    // This is where the session that this Thread is handling right now will be stored
    private final static ThreadLocal<RwsSession> session = new ThreadLocal<RwsSession>();

    private final Logger log = LoggerFactory.getLogger(RwsSession.class);

    public String getId() {
        return id;
    }

    public RwsContext getContext() {
        return context;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        fireChange(this);
    }

    public RwsSession(RwsContext context, RwsWebSocketAdapter adapter) {
        this.context = context;
        this.adapter = adapter;
        id = Long.toString(nextSessionId++);
        name = "Client #" + id;
        attributes = new HashMap<String, Object>();
        subscriptions = new HashMap<String, Subscription>();
        listeners = new HashMap<String, EventListener>();
        groups = new HashSet<String>();
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

    public void send(String from, JSONObject data) throws IOException {
        data.put("from", from);
        String jsonText = JSONValue.toJSONString(data);
        adapter.sendMessage(jsonText);
    }

    public boolean isConnected() {
        return adapter.isConnected();
    }

    public void disconnect() {
        // Unsubscribe from all events
        ArrayList<Subscription> subs = new ArrayList(subscriptions.values());
        for (Subscription sub : subs) {
            try {
                unsubscribe(sub);
            } catch (Throwable th) {
                // Ignore
            }
        }

        // Disconnect socket
        try {
            adapter.disconnect();
        } catch (Throwable th) {
            // Ignore
        }
    }

    public void join(String group) {
        if (!groups.contains(group)) {
            groups.add(group);
            context.fireJoin(group, this);
        }
    }

    public void leave(String group) {
        if (groups.contains(group)) {
            groups.remove(group);
            context.fireLeave(group, this);
        }
    }

    public Collection<String> listMulticastGroups() {
        return Collections.unmodifiableCollection(groups);
    }

    public void fireChange(RwsSession client) {
        context.fireChange(this);
    }

    public void subscribe(final Subscription sub) throws RwsException, InvocationTargetException {
        if (listeners.containsKey(sub.getHandlerId())) {
            throw new RwsException("An event handler with the id '" + sub.getHandlerId() + "' already exists");
        }

        RwsEventHandler handler = new RwsEventHandler() {
            @Override
            public void handleEvent(Object data) throws IOException {
                send("sys", newEvent(sub.getHandlerId(), data));
            }
        };

        EventListener listener = context.getRegistry().subscribe(this, sub.getObject(), sub.getEvent(), sub.getAction(), handler);
        
        subscriptions.put(sub.getHandlerId(), sub);
        listeners.put(sub.getHandlerId(), listener);
    }

    public void unsubscribe(Subscription sub) throws RwsException, InvocationTargetException {
        EventListener listener = listeners.get(sub.getHandlerId());
        if (listener != null) {
            context.getRegistry().unsubscribe(this, sub.getObject(), sub.getEvent(), listener);
            subscriptions.remove(sub.getHandlerId());
            listeners.remove(sub.getHandlerId());
        }
    }

    private JSONObject newEvent(String id, Object data) {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("event", data);
        return obj;
    }

    public static class Subscription {
        private String clientId;
        private String handlerId;
        private String action;
        private String event;
        private String object;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public String getHandlerId() {
            return handlerId;
        }

        public void setHandlerId(String handlerId) {
            this.handlerId = handlerId;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

    }

    public void handleMessage(JSONObject info) throws IOException {
        String to = (String) info.get("to");
        if (to == null || "sys".equals(to)) {
            // The message is for the server
            doCall(info);
        } else if ("all".equals(to)) {
            // Send the message to all connected sockets
            context.sendAll(getId(), info, false);
        } else if (to.startsWith("#")) {
            // Send the message to all sockets in the named group
            String group = to.substring(1);
            context.sendMulti(getId(), group, info, false);
        } else {
            // Send the message to the indicated socket
            context.sendTo(getId(), to, info);
        }
    }

    private void doCall(JSONObject info) throws IOException {
        String returnId = (String) info.get("id"); // If null the caller is not interested in the result!
        String obj = (String) info.get("object");
        String method = (String) info.get("method");
        Object params = (Object) info.get("params");

        Object[] args = null;
        // Convert parameter map to array
        if (params != null && params instanceof JSONArray) {
            JSONArray p = (JSONArray) params;
            args = new Object[p.size()];
            for (int i = 0; i < p.size(); i++) {
                args[i] = p.get(i);
            }
        }

        try {
            Object result = context.getRegistry().call(this, obj, method, args);
            if (returnId != null) {
                send("sys", newCallResult(returnId, result));
            }
        } catch (InvocationTargetException ex) {
            log.error("Remote object returned an error", ex);
            if (returnId != null) {
                send("sys", newCallException(returnId, ex));
            }
        } catch (Throwable th) {
            log.error("Rws Call failed", th);
            if (returnId != null) {
                send("sys", newCallException(returnId, th));
            }
        }
    }

    private JSONObject newCallResult(String returnId, Object data) {
        JSONObject obj = new JSONObject();
        obj.put("id", returnId);
        obj.put("result", data);
        return obj;
    }

    private JSONObject newCallException(String returnId, Throwable th) {
        JSONObject obj = new JSONObject();
        obj.put("id", returnId);
        obj.put("exception", th.toString());
        return obj;
    }

    public static RwsSession getInstance() {
        return session.get();
    }

    public static void setInstance(RwsSession session) {
        RwsSession.session.set(session);
    }
}
