
package org.codejive.rws;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private static final List<ConverterInfo> rwsConverters = new ArrayList<ConverterInfo>();

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
    
    public static Object call(RwsSession context, String objName, String method, Object[] args) throws RwsException, InvocationTargetException {
        log.debug("Calling method {} on object {}", method, objName);
        RwsObject obj = rwsObjects.get(objName);
        if (obj != null) {
            return obj.call(context, method, args);
        } else {
            throw new RwsException("Unknown object '" + objName + "'");
        }
    }

    public static class ConverterInfo {
        private RwsConverter converter;
        private String match;

        public ConverterInfo(RwsConverter converter, String match) {
            this.converter = converter;
            this.match = match;
        }
    }

    public static void register(RwsConverter conv, String match) {
        log.info("Registering converter {}", conv);
        ConverterInfo convInfo = new ConverterInfo(conv, match);
        rwsConverters.add(convInfo);
    }

    public static void unregister(ConverterInfo convInfo) {
        log.info("Un-registering converter {}", convInfo);
        rwsConverters.remove(convInfo);
    }

    public static Object convertToJSON(Object value) throws RwsException {
        Object result = null;
        if (value != null) {
            RwsConverter conv = RwsRegistry.findConverter(value.getClass());
            if (conv != null) {
                result = conv.toJSON(value);
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
            RwsConverter conv = RwsRegistry.findConverter(targetType);
            if (conv != null) {
                result = conv.fromJSON(value, targetType);
            } else if (targetType.isAssignableFrom(value.getClass())) {
                result = value;
            } else {
                result = value.toString();
            }
        }
        return result;
    }

    private static RwsConverter findConverter(Class type) {
        RwsConverter result = null;
        for (ConverterInfo convInfo : rwsConverters) {
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
                result = convInfo.converter;
                break;
            }
        }
        return result;
    }
}
