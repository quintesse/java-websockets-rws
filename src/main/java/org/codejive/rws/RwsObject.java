
package org.codejive.rws;

import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final Set<String> methodNames;
    private final boolean include;

    private Map<String, MethodDescriptor> allowedMethods;
    private Map<String, EventSetDescriptor> allowedEvents;
    
    private Object targetObject;
    
    public enum Scope { call, connection, global };

    private static final Logger log = LoggerFactory.getLogger(RwsRegistry.class);

    public String getName() {
        return name;
    }

    public Class getTargetClass() {
        return targetClass;
    }

    public RwsObject(String name, Class targetClass, Scope scope) throws RwsException {
        this(name, targetClass, scope, (Collection<String>)null, false);
    }

    public RwsObject(String name, Class targetClass, Scope scope, Collection<String> methodNames, boolean include) throws RwsException {
        this.name = name;
        this.targetClass = targetClass;
        this.scope = scope;
        this.methodNames = new HashSet<String>();
        if (methodNames != null) {
            this.methodNames.addAll(methodNames);
        }
        this.include = include;

        try {
            BeanInfo info = Introspector.getBeanInfo(targetClass);
            
            allowedMethods = new HashMap<String, MethodDescriptor>();
            MethodDescriptor[] methodDefs = info.getMethodDescriptors();
            for (MethodDescriptor md : methodDefs) {
                boolean contains = this.methodNames.contains(md.getName());
                if ((this.include && contains) || (!this.include && !contains)) {
                    if (!allowedMethods.containsKey(md.getName())) {
                        allowedMethods.put(md.getName(), md);
                    } else {
                        // If a method with the same name was already added once
                        // before we're dealing with an overloaded method which
                        // is not supported, so we flag it for later removal
                        allowedMethods.put(md.getName(), null);
                    }
                }
            }
            // Remove overloaded methods
            Set<String> mnms = new HashSet<String>(allowedMethods.keySet());
            for (String mnm : mnms) {
                if (allowedMethods.get(mnm) == null) {
                    allowedMethods.remove(mnm);
                }
            }
            // Remove forbidden methods
            allowedMethods.remove("getClass");
            allowedMethods.remove("hashCode");
            allowedMethods.remove("equals");
            allowedMethods.remove("notify");
            allowedMethods.remove("notifyAll");
            allowedMethods.remove("wait");

            allowedEvents = new HashMap<String, EventSetDescriptor>();
            EventSetDescriptor[] eventSets = info.getEventSetDescriptors();
            for (EventSetDescriptor es : eventSets) {
                allowedEvents.put(es.getName(), es);
            }
        } catch (IntrospectionException ex) {
            throw new RwsException("Could not get necessary information about target object", ex);
        }
    }

    public RwsObject(String name, Class targetClass, Scope scope, String[] methodNames, boolean include) throws RwsException {
        this(name, targetClass, scope, Arrays.asList(methodNames), include);
    }

    public Set<String> listMethodNames() {
        return Collections.unmodifiableSet(allowedMethods.keySet());
    }

    public MethodDescriptor getTargetMethod(String methodName) {
        return allowedMethods.get(methodName);
    }

    public Set<String> listEventNames() {
        return Collections.unmodifiableSet(allowedEvents.keySet());
    }

    public EventSetDescriptor getTargetEvent(String methodName) {
        return allowedEvents.get(methodName);
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
            MethodDescriptor method = getTargetMethod(methodName);
            if (method != null) {
                Object[] convertedArgs = null;
                if (args != null) {
                    convertedArgs = new Object[args.length];
                    for (int i = 0; i < args.length; i++) {
                        Class paramClass = method.getMethod().getParameterTypes()[i];
                        convertedArgs[i] = RwsRegistry.convertFromJSON(args[i], paramClass);
                    }
                }
                Object tmpResult = method.getMethod().invoke(obj, convertedArgs);
                result = RwsRegistry.convertToJSON(tmpResult);
            } else {
                throw new RwsException("Method '" + methodName + "' does not exist for object '" + name + "'");
            }
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not call method '" + methodName + "' on object '" + name + "'", ex);
        } catch (IllegalArgumentException ex) {
            throw new RwsException("Could not call method '" + methodName + "' on object '" + name + "'", ex);
        }
        return result;
    }
}
