
package org.codejive.rws;

import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsObject {
    private final String match;
    private final Class targetClass;
    private final String jsName;
    private final RwsConverter converter;
    private final Set<String> methodNames;
    private final Set<String> eventNames;
    private final Set<String> propNames;

    private boolean includeMethods;
    private boolean includeEvents;
    private boolean includeProps;
    private Map<String, MethodDescriptor> allowedMethods;
    private Map<String, EventSetDescriptor> allowedEvents;
    private Map<String, PropertyDescriptor> allowedProps;
    
    private static final Logger log = LoggerFactory.getLogger(RwsObject.class);

    public String getMatch() {
        return match;
    }

    public Class getTargetClass() {
        return targetClass;
    }

    public RwsObject(Class targetClass, String scriptName, RwsConverter converter) throws RwsException {
        this.match = targetClass.getName(); // TODO This has to be configurable
        this.targetClass = targetClass;
        this.jsName = scriptName;
        this.converter = converter;
        this.methodNames = new HashSet<String>();
        this.eventNames = new HashSet<String>();
        this.propNames = new HashSet<String>();
        this.includeMethods = false;
        this.includeEvents = false;
        this.includeProps = false;
        init();
    }

    private void init() throws RwsException {
        try {
            BeanInfo info = Introspector.getBeanInfo(targetClass);

            allowedMethods = new HashMap<String, MethodDescriptor>();
            MethodDescriptor[] methodDefs = info.getMethodDescriptors();
            for (MethodDescriptor md : methodDefs) {
                boolean contains = this.methodNames.contains(md.getName());
                if ((this.includeMethods && contains) || (!this.includeMethods && !contains)) {
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
            allowedMethods.remove("toString");
            allowedMethods.remove("wait");

            allowedEvents = new HashMap<String, EventSetDescriptor>();
            EventSetDescriptor[] eventSets = info.getEventSetDescriptors();
            for (EventSetDescriptor es : eventSets) {
                boolean contains = this.eventNames.contains(es.getName());
                if ((this.includeEvents && contains) || (!this.includeEvents && !contains)) {
                    allowedEvents.put(es.getName(), es);
                }
            }

            allowedProps = new HashMap<String, PropertyDescriptor>();
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            for (PropertyDescriptor p : props) {
                boolean contains = this.propNames.contains(p.getName());
                if ((this.includeProps && contains) || (!this.includeProps && !contains)) {
                    allowedProps.put(p.getName(), p);
                }
            }
        } catch (IntrospectionException ex) {
            throw new RwsException("Could not get necessary information about target object", ex);
        }
    }

    public String scriptName() {
        return (jsName != null) ? jsName : targetClass.getSimpleName();
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

    public Set<String> listPropertyNames() {
        return Collections.unmodifiableSet(allowedProps.keySet());
    }

    public PropertyDescriptor getTargetProperty(String propertyName) {
        return allowedProps.get(propertyName);
    }

    public Object call(RwsSession session, Object instance, String methodName, Object[] args) throws RwsException, InvocationTargetException {
        Object result = null;
        try {
            MethodDescriptor method = getTargetMethod(methodName);
            if (method != null) {
                Object[] convertedArgs = null;
                if (args != null) {
                    convertedArgs = new Object[args.length];
                    for (int i = 0; i < args.length; i++) {
                        Class paramClass = method.getMethod().getParameterTypes()[i];
                        convertedArgs[i] = session.getContext().getRegistry().convertFromJSON(args[i], paramClass);
                    }
                }
                Object tmpResult = method.getMethod().invoke(instance, convertedArgs);
                result = session.getContext().getRegistry().convertToJSON(tmpResult);
            } else {
                throw new RwsException("Method '" + methodName + "' does not exist for object '" + jsName + "'");
            }
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not call method '" + methodName + "' on object '" + jsName + "'", ex);
        } catch (IllegalArgumentException ex) {
            throw new RwsException("Could not call method '" + methodName + "' on object '" + jsName + "'", ex);
        }
        return result;
    }

    public EventListener subscribe(final RwsSession session, Object instance, String eventName, final String action, final RwsEventHandler handler) throws RwsException, InvocationTargetException {
        EventListener listener;
        try {
            EventSetDescriptor event = getTargetEvent(eventName);
            if (event != null) {
                Class listenerType = event.getListenerType();

                // Check if the action that was passed matches one of the methods of the listener interface
                Method[] ms = event.getListenerMethods();
                boolean found = false;
                for (int i = 0; i < ms.length && !found; i++) {
                    found = ms[i].getName().equals(action);
                }
                if (!found) {
                    throw new RwsException("Action " + action + " does not exist for event '" + eventName + "' on object '" + jsName + "'");
                }

                // Create the event listener proxy that will call the event handler
                Class[] interfaces = new Class[] { listenerType };
                InvocationHandler handlerWrapper = new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals(action)) {
                            handler.handleEvent(session.getContext().getRegistry().convertToJSON(args));
                            return null;
                        } else if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(handler, args);
                        } else {
                            return null;
                        }
                    }
                };
                listener = (EventListener) Proxy.newProxyInstance(listenerType.getClassLoader(), interfaces, handlerWrapper);

                // Add the newly created proxy event listener to the object
                Method addListener = event.getAddListenerMethod();
                addListener.invoke(instance, new Object[] { listener });
            } else {
                throw new RwsException("Event '" + eventName + "' does not exist for object '" + jsName + "'");
            }
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not subscribe to event '" + eventName + "' on object '" + jsName + "'", ex);
        } catch (IllegalArgumentException ex) {
            throw new RwsException("Could not subscribe to event '" + eventName + "' on object '" + jsName + "'", ex);
        }
        return listener;
    }

    public void unsubscribe(RwsSession session, Object instance, String eventName, EventListener listener) throws RwsException, InvocationTargetException {
        try {
            EventSetDescriptor event = getTargetEvent(eventName);
            if (event != null) {
                Method removeListener = event.getRemoveListenerMethod();
                removeListener.invoke(instance, new Object[] { listener });
            } else {
                throw new RwsException("Event '" + eventName + "' does not exist for object '" + jsName + "'");
            }
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not unsubscribe from event '" + eventName + "' on object '" + jsName + "'", ex);
        } catch (IllegalArgumentException ex) {
            throw new RwsException("Could not unsubscribe from event '" + eventName + "' on object '" + jsName + "'", ex);
        }
    }

    Object toJSON(Object value) throws RwsException {
        JSONObject result = (JSONObject) converter.toJSON(this, value);
        result.put("class", scriptName());
        return result;
    }

    Object fromJSON(Object value, Class targetType) throws RwsException {
        return converter.fromJSON(this, value);
    }

    public void generateTypeScript(String instanceName, PrintWriter out) throws RwsException {
        converter.generateTypeScript(this, instanceName, out);
    }
}
