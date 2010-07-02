
package org.codejive.rws;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsRegistry {
    private static final Map<String, RwsObject> rwsObjects = new HashMap<String, RwsObject>();
    private static final List<Conversion> rwsConverters = new ArrayList<Conversion>();

    private static final Logger log = LoggerFactory.getLogger(RwsRegistry.class);

    public static void register(RwsObject obj) {
        log.info("Registering object {}", obj);
        rwsObjects.put(obj.getName(), obj);
    }

    public static void unregister(RwsObject obj) {
        log.info("Un-registering object {}", obj);
        rwsObjects.remove(obj.getName());
    }

    public static Collection<RwsObject> getObjects() {
        return Collections.unmodifiableCollection(rwsObjects.values());
    }

    public static Set<String> getObjectNames() {
        return Collections.unmodifiableSet(rwsObjects.keySet());
    }

    public static RwsObject getObject(String objName) {
        return rwsObjects.get(objName);
    }
    
    private static RwsObject getObjectStrict(String objName) throws RwsException {
        RwsObject result = rwsObjects.get(objName);
        if (result == null) {
            throw new RwsException("Unknown object '" + objName + "'");
        }
        return result;
    }

    public static Object call(RwsSession context, String objName, String method, Object[] args) throws RwsException, InvocationTargetException {
        log.debug("Calling method {} on object {}", method, objName);
        RwsObject obj = getObjectStrict(objName);
        return obj.call(context, method, args);
    }

    public static EventListener subscribe(RwsSession context, String objName, String event, String action, RwsEventHandler handler) throws RwsException, InvocationTargetException {
        if (log.isDebugEnabled()) log.debug("Subscribing to action {} on event {} on object {}", new Object[] { action, event, objName });
        RwsObject obj = getObjectStrict(objName);
        return obj.subscribe(context, event, action, handler);
    }

    public static void unsubscribe(RwsSession context, String objName, String event, EventListener listener) throws RwsException, InvocationTargetException {
        log.debug("Unsubscribing from from event {} on object {}", event, objName);
        RwsObject obj = getObjectStrict(objName);
        obj.unsubscribe(context, event, listener);
    }

    public static class Conversion {
        private String name;
        private RwsConverter converter;
        private String match;

        public Conversion(String name, RwsConverter converter, String match) {
            this.name = name;
            this.converter = converter;
            this.match = match;
        }

        public String getName() {
            return name;
        }

        public RwsConverter getConverter() {
            return converter;
        }

        public String getMatch() {
            return match;
        }
    }

    public static void register(RwsConverter conv, String match) {
        register(null, conv, match);
    }

    public static void register(String name, RwsConverter conv, String match) {
        log.info("Registering converter {}", conv);
        Conversion convInfo = new Conversion(name, conv, match);
        rwsConverters.add(convInfo);
    }

    public static void unregister(Conversion convInfo) {
        log.info("Un-registering converter {}", convInfo);
        rwsConverters.remove(convInfo);
    }

    public static Object convertToJSON(Object value) throws RwsException {
        Object result = null;
        if (value != null) {
            Conversion conv = RwsRegistry.findConversion(value.getClass());
            if (conv != null) {
                String name = conv.getName();
                if (name == null) {
                    name = value.getClass().getSimpleName();
                }
                result = conv.getConverter().toJSON(value, name);
            } else if (value instanceof JSONAware) {
                result = value;
            } else if (value instanceof Iterable) {
                JSONArray arr = new JSONArray();
                Iterable iter = (Iterable) value;
                for (Object val : iter) {
                    arr.add(convertToJSON(val));
                }
                result = arr;
            } else if (value.getClass().isArray()) {
                JSONArray arr = new JSONArray();
                Object[] values = (Object[]) value;
                for (Object val : values) {
                    arr.add(convertToJSON(val));
                }
                result = arr;
            } else {
                result = value.toString();
            }
        }
        return result;
    }

    public static Object convertFromJSON(Object value, Class targetType) throws RwsException {
        Object result = null;
        if (value != null) {
            Conversion conv = RwsRegistry.findConversion(targetType);
            if (conv != null) {
                result = conv.getConverter().fromJSON(value, targetType);
            } else if (targetType.isAssignableFrom(value.getClass())) {
                result = value;
            } else {
                result = value.toString();
            }
        }
        return result;
    }

    public static void generateTypeScript(Class type, PrintWriter out) throws RwsException {
        Conversion conv = RwsRegistry.findConversion(type);
        if (conv != null) {
            String name = conv.getName();
            if (name == null) {
                name = type.getSimpleName();
            }
            conv.getConverter().generateTypeScript(name, type, out);
        }
    }

    public static Conversion findConversion(Class type) {
        Conversion result = null;
        for (Conversion convInfo : rwsConverters) {
            boolean matches;
            if (convInfo.match.startsWith("*") && convInfo.match.endsWith("*")) {
                String match = convInfo.match.substring(1, convInfo.match.length() - 1);
                matches = type.getName().contains(match);
            } else if (convInfo.match.endsWith("*")) {
                String match = convInfo.match.substring(0, convInfo.match.length() - 1);
                matches = type.getName().startsWith(match);
            } else if (convInfo.match.startsWith("*")) {
                String match = convInfo.match.substring(1);
                matches = type.getName().endsWith(match);
            } else {
                matches = type.getName().equals(convInfo.match);
            }
            if (matches) {
                result = convInfo;
                break;
            }
        }
        return result;
    }
}
