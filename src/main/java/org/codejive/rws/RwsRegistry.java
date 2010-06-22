
package org.codejive.rws;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsRegistry {
    private static final Map<String, RwsObject> rmiObjects = new HashMap<String, RwsObject>();

    private static final Logger log = LoggerFactory.getLogger(RwsRegistry.class);

    public static void register(RwsObject obj) {
        log.info("Registering object {}", obj);
        rmiObjects.put(obj.getName(), obj);
    }

    public static void unregister(RwsObject obj) {
        log.info("Un-registering object {}", obj);
        rmiObjects.remove(obj.getName());
    }

    public static Collection<RwsObject> getObjects() {
        return Collections.unmodifiableCollection(rmiObjects.values());
    }

    public static Set<String> getObjectNames() {
        return Collections.unmodifiableSet(rmiObjects.keySet());
    }

    public static RwsObject getObject(String objName) {
        return rmiObjects.get(objName);
    }
    
    public static Object call(RwsSession context, String objName, String method, Object[] args) throws RwsException, InvocationTargetException {
        log.debug("Calling method {} on object {}", method, objName);
        RwsObject obj = rmiObjects.get(objName);
        if (obj != null) {
            return obj.call(context, method, args);
        } else {
            throw new RwsException("Unknown object '" + objName + "'");
        }
    }
}
