
package org.codejive.rws;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
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

//    public enum Scope { session, global };

    private static final Logger log = LoggerFactory.getLogger(RwsRegistry.class);

    public static void register(RwsObject obj) {
        log.info("Registering object {}", obj);
        rwsObjects.put(obj.scriptName(), obj);
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

    public static RwsObject matchObject(Class type) {
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

    public static Object call(RwsSession session, String objName, String method, Object[] args) throws RwsException, InvocationTargetException {
        log.debug("Calling method {} on object {}", method, objName);
        RwsObject obj = getObjectStrict(objName);
        Object instance = getInstance();
        return obj.call(session, instance, method, args);
    }

    public static EventListener subscribe(RwsSession session, String objName, String event, String action, RwsEventHandler handler) throws RwsException, InvocationTargetException {
        if (log.isDebugEnabled()) log.debug("Subscribing to action {} on event {} on object {}", new Object[] { action, event, objName });
        RwsObject obj = getObjectStrict(objName);
        Object instance = getInstance();
        return obj.subscribe(session, instance, event, action, handler);
    }

    public static void unsubscribe(RwsSession session, String objName, String event, EventListener listener) throws RwsException, InvocationTargetException {
        log.debug("Unsubscribing from from event {} on object {}", event, objName);
        RwsObject obj = getObjectStrict(objName);
        Object instance = getInstance();
        obj.unsubscribe(session, instance, event, listener);
    }

    public static Object convertToJSON(Object value) throws RwsException {
        Object result = null;
        if (value != null) {
            RwsObject obj = RwsRegistry.matchObject(value.getClass());
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

    public static Object convertFromJSON(Object value, Class targetType) throws RwsException {
        Object result = null;
        if (value != null) {
            RwsObject obj = RwsRegistry.matchObject(targetType);
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

    public static void generateTypeScript(Class type, PrintWriter out) throws RwsException {
        RwsObject obj = RwsRegistry.matchObject(type);
        if (obj != null) {
            String instanceName = obj.scriptName(); // TODO Get real instance name!!
            obj.generateTypeScript(instanceName, out);
        }
    }

    public static Object getInstance() throws RwsException {
        return null;
    }
    
//    private Object createTargetObject() throws RwsException {
//        Object result;
//        log.info("Creating target object for '{}'", jsName);
//        try {
//            result = targetClass.newInstance();
//        } catch (InstantiationException ex) {
//            throw new RwsException("Could not create target object for '" + jsName + "'", ex);
//        } catch (IllegalAccessException ex) {
//            throw new RwsException("Could not create target object for '" + jsName + "'", ex);
//        }
//        return result;
//    }
//
//    private String getTargetObjectName() {
//        return "__rws__" + jsName;
//    }
//
//    public Object getTargetObject(RwsSession session) throws RwsException {
//        Object result = null;
//        switch (scope) {
//            case session:
//                result = session.getAttribute(getTargetObjectName());
//                break;
//            case global:
//                result = session.getContext().getAttribute(getTargetObjectName());
//                break;
//        }
//        if (result == null) {
//            result = createTargetObject();
//            setTargetObject(session, result);
//        }
//        return result;
//    }
//
//    public Object getTargetObject(RwsContext context) throws RwsException {
//        Object result = null;
//        switch (scope) {
//            case global:
//                result = context.getAttribute(getTargetObjectName());
//                break;
//        }
//        if (result == null) {
//            result = createTargetObject();
//            setTargetObject(context, result);
//        }
//        return result;
//    }
//
//    public void setTargetObject(RwsSession session, Object targetObject) {
//        switch (scope) {
//            case session:
//                session.setAttribute(getTargetObjectName(), targetObject);
//                break;
//            case global:
//                session.getContext().setAttribute(getTargetObjectName(), targetObject);
//                break;
//        }
//    }
//
//    public void setTargetObject(RwsContext context, Object targetObject) {
//        switch (scope) {
//            case global:
//                context.setAttribute(getTargetObjectName(), targetObject);
//                break;
//        }
//    }

}
