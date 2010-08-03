
package org.codejive.rws;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
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
    private final Map<String, RwsObject> rwsObjects = new HashMap<String, RwsObject>();
    private final Map<String, InstanceInfo> instances = new HashMap<String, InstanceInfo>();

    public enum Scope { session, global };

    private static ThreadLocal<Set<Class>> generatedTypes = new ThreadLocal<Set<Class>>();

    private static final Logger log = LoggerFactory.getLogger(RwsRegistry.class);

    public void register(RwsObject obj) {
        log.info("Registering object {}", obj);
        rwsObjects.put(obj.scriptName(), obj);
    }

    public void register(RwsObject obj, RwsContext context, String instanceName, Object instance) {
        log.info("Registering instance {} with name '{}'", instance, instanceName);
        if (!rwsObjects.containsKey(obj.scriptName())) {
            register(obj);
        }
        InstanceInfo ii = new InstanceInfo(obj, instanceName);
        ii.setInstance(context, instance);
        instances.put(instanceName, ii);
    }

    public void register(RwsObject obj, RwsSession session, String instanceName, Object instance) {
        log.info("Registering instance {} with name '{}'", instance, instanceName);
        if (!rwsObjects.containsKey(obj.scriptName())) {
            register(obj);
        }
        InstanceInfo ii = new InstanceInfo(obj, instanceName);
        ii.setInstance(session, instance);
        instances.put(instanceName, ii);
    }

    public class InstanceInfo {
        private final RwsObject object;
        private final String instanceName;
        private final String attrName;

        public InstanceInfo(RwsObject object, String instanceName) {
            this.object = object;
            this.instanceName = instanceName;
            attrName = "__rws__" + instanceName;
        }

        public Object getInstance(RwsContext context) {
            return context.getAttribute(attrName);
        }

        public void setInstance(RwsContext context, Object instance) {
            if (instance != null) {
                context.setAttribute(attrName, instance);
            } else {
                context.removeAttribute(attrName);
            }
        }

        public Object getInstance(RwsSession session) {
            Object result = session.getAttribute(attrName);
            if (result == null) {
                result = session.getContext().getAttribute(attrName);
            }
            return result;
        }

        public void setInstance(RwsSession session, Object instance) {
            session.setAttribute(attrName, instance);
        }
    }

    public RwsObject getObject(String objName) {
        return rwsObjects.get(objName);
    }
    
    public Set<String> listObjectNames() {
        return Collections.unmodifiableSet(rwsObjects.keySet());
    }

    public RwsObject matchObject(Class type) {
        RwsObject result = null;
        for (RwsObject obj : rwsObjects.values()) {
            boolean matches;
            String match = obj.getMatch();
            if (match.startsWith("*") && match.endsWith("*")) {
                String m = match.substring(1, match.length() - 1);
                matches = type.getName().contains(m);
            } else if (match.endsWith("*")) {
                String m= match.substring(0, match.length() - 1);
                matches = type.getName().startsWith(m);
            } else if (match.startsWith("*")) {
                String m = match.substring(1);
                matches = type.getName().endsWith(m);
            } else {
                matches = type.getName().equals(match);
            }
            if (matches) {
                result = obj;
                break;
            }
        }
        return result;
    }

    public InstanceInfo getInstanceInfo(String instanceName) {
        return instances.get(instanceName);
    }

    public Set<String> listInstanceNames(String objName) {
        Set<String> result = new HashSet<String>();
        for (InstanceInfo ii : instances.values()) {
            if (ii.object.scriptName().equals(objName)) {
                result.add(ii.instanceName);
            }
        }
        return result;
    }

    private InstanceInfo getInstanceInfoStrict(String instanceName) throws RwsException {
        InstanceInfo result = instances.get(instanceName);
        if (result == null) {
            throw new RwsException("Unknown instance '" + instanceName + "'");
        }
        return result;
    }

    public Object call(RwsSession session, String instanceName, String method, Object[] args) throws RwsException, InvocationTargetException {
        log.debug("Calling method {} on instance {}", method, instanceName);
        InstanceInfo ii = getInstanceInfoStrict(instanceName);
        return ii.object.call(session, ii.getInstance(session), method, args);
    }

    public EventListener subscribe(RwsSession session, String instanceName, String event, String action, RwsEventHandler handler) throws RwsException, InvocationTargetException {
        if (log.isDebugEnabled()) log.debug("Subscribing to action {} on event {} on instance {}", new Object[] { action, event, instanceName });
        InstanceInfo ii = getInstanceInfoStrict(instanceName);
        return ii.object.subscribe(session, ii.getInstance(session), event, action, handler);
    }

    public void unsubscribe(RwsSession session, String instanceName, String event, EventListener listener) throws RwsException, InvocationTargetException {
        log.debug("Unsubscribing from from event {} on instance {}", event, instanceName);
        InstanceInfo ii = getInstanceInfoStrict(instanceName);
        ii.object.unsubscribe(session, ii.getInstance(session), event, listener);
    }

    public Object convertToJSON(Object value) throws RwsException {
        Object result = null;
        if (value != null) {
            RwsObject obj = matchObject(value.getClass());
            if (obj != null) {
                result = obj.toJSON(value);
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

    public Object convertFromJSON(Object value, Class targetType) throws RwsException {
        Object result = null;
        if (value != null) {
            RwsObject obj = matchObject(targetType);
            if (obj != null) {
                result = obj.fromJSON(value, targetType);
            } else if (targetType.isAssignableFrom(value.getClass())) {
                result = value;
            } else {
                result = value.toString();
            }
        }
        return result;
    }

    public void generateTypeScript(Class type, PrintWriter out) throws RwsException {
        // The following is a bit of a hack to prevent duplicate types or even
        // getting stuck in a recursive loop without making the API more complex.
        boolean first = false;
        Set<Class> types = generatedTypes.get();
        if (generatedTypes.get() == null) {
            types = new HashSet<Class>();
            generatedTypes.set(types);
            first = true;
        }

        if (!types.contains(type)) {
            types.add(type);
            RwsObject obj = matchObject(type);
            if (obj != null) {
                obj.generateTypeScript(out);
            }
        }

        if (first) {
            generatedTypes.set(null);
        }
    }

}
