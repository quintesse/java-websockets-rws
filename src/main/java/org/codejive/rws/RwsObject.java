
package org.codejive.rws;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsObject {
    private final String name;
    private final Class targetClass;
    private final Scope scope;
    private final Set<String> methodNames = new HashSet<String>();
    private final boolean include;

    private Object targetObject;
    private Set<String> allowedMethods;
    
    public enum Scope { call, connection, global };

    private static final Logger log = LoggerFactory.getLogger(RwsRegistry.class);

    public String getName() {
        return name;
    }

    public Class getTargetClass() {
        return targetClass;
    }

    public RwsObject(String name, Class targetClass, Scope scope) {
        this(name, targetClass, scope, (Collection<String>)null, false);
    }

    public RwsObject(String name, Class targetClass, Scope scope, Collection<String> methodNames, boolean include) {
        this.name = name;
        this.targetClass = targetClass;
        this.scope = scope;
        if (methodNames != null) {
            this.methodNames.addAll(methodNames);
        }
        this.include = include;

        if (!include) {
            this.methodNames.add("getClass");
            this.methodNames.add("hashCode");
            this.methodNames.add("equals");
            this.methodNames.add("notify");
            this.methodNames.add("notifyAll");
            this.methodNames.add("wait");
        }
    }

    public RwsObject(String name, Class targetClass, Scope scope, String[] methodNames, boolean include) {
        this(name, targetClass, scope, Arrays.asList(methodNames), include);
    }

    public synchronized Set<String> listMethodNames() {
        if (allowedMethods == null) {
            allowedMethods = new HashSet<String>();
            Method[] allMethods = targetClass.getMethods();
            for (Method m : allMethods) {
                boolean contains =  methodNames.contains(m.getName());
                if ((include && contains) || (!include && !contains)) {
                    allowedMethods.add(m.getName());
                }
            }
        }
        return Collections.unmodifiableSet(allowedMethods);
    }

    public Method getTargetMethod(String methodName) throws RwsException {
        Method[] allMethods = targetClass.getMethods();
        ArrayList<Method> methods = new ArrayList<Method>();
        for (Method m : allMethods) {
            if (m.getName().equals(methodName)) {
                methods.add(m);
            }
        }
        if (methods.size() > 1) {
            throw new RwsException("Overloaded method '" + methodName + "' for object '" + name + "', which is not supported");
        } else if (methods.isEmpty()) {
            throw new RwsException("Couldn't find matching method '" + methodName + "' for object '" + name + "'");
        }
        return methods.get(0);
    }

    public Object getTargetObject(RwsSession context) throws RwsException {
        Object result = null;
        switch (scope) {
            case connection:
                result = context.getAttribute("__rws__" + name);
                break;
            case global:
                result = targetObject;
                break;
        }
        if (result == null) {
            log.info("Creating target object for '{}'", name);
            try {
                result = targetClass.newInstance();
                setTargetObject(context, result);
            } catch (InstantiationException ex) {
                throw new RwsException("Could not create target object for '" + name + "'", ex);
            } catch (IllegalAccessException ex) {
                throw new RwsException("Could not create target object for '" + name + "'", ex);
            }
        }
        return result;
    }

    public void setTargetObject(RwsSession context, Object targetObject) {
        switch (scope) {
            case connection:
                context.setAttribute("__rws__" + name, targetObject);
                break;
            case global:
                this.targetObject = targetObject;
                break;
        }
    }

    public Object call(RwsSession context, String methodName, Object[] args) throws RwsException, InvocationTargetException {
        Object result = null;
        try {
            Object obj = getTargetObject(context);
            Method method = getTargetMethod(methodName);
            Object[] convertedArgs = null;
            if (args != null) {
                convertedArgs = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    Class paramClass = method.getParameterTypes()[i];
                    convertedArgs[i] = RwsRegistry.convertFromJSON(args[i], paramClass);
                }
            }
            Object tmpResult = method.invoke(obj, convertedArgs);
            result = RwsRegistry.convertToJSON(tmpResult);
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not call method '" + methodName + "' on object '" + name + "'", ex);
        } catch (IllegalArgumentException ex) {
            throw new RwsException("Could not call method '" + methodName + "' on object '" + name + "'", ex);
        }
        return result;
    }
}
