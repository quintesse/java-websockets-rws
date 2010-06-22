package org.codejive.rws;

import java.io.IOException;
import java.util.Collections;
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
    private RwsWebSocketAdapter adapter;
    private String id;

    private HashMap<String, Object> attributes;

    private static long nextSessionId = 1;

    private final Logger log = LoggerFactory.getLogger(RwsSession.class);

    public String getId() {
        return id;
    }

    public RwsSession(RwsWebSocketAdapter adapter) {
        this.adapter = adapter;
        id = Long.toString(nextSessionId++);
        attributes = new HashMap<String, Object>();
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
}
