package org.codejive.rws;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Set;
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

    private static long nextSessionId = 1;

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

        EventListener listener = RwsRegistry.subscribe(this, sub.getObject(), sub.getEvent(), sub.getAction(), handler);
        
        subscriptions.put(sub.getHandlerId(), sub);
        listeners.put(sub.getHandlerId(), listener);
    }

    public void unsubscribe(Subscription sub) throws RwsException, InvocationTargetException {
        EventListener listener = listeners.get(sub.getHandlerId());
        if (listener != null) {
            RwsRegistry.unsubscribe(this, sub.getObject(), sub.getEvent(), listener);
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
}
