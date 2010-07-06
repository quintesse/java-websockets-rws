
package org.codejive.rws.converters;

import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.codejive.rws.RwsConverter;
import org.codejive.rws.RwsException;
import org.codejive.rws.RwsObject;
import org.codejive.rws.RwsRegistry;
import org.codejive.rws.utils.Strings;
import org.json.simple.JSONObject;

/**
 *
 * @author tako
 */
public class RwsBeanConverter implements RwsConverter<Object> {
    private RwsRegistry registry;

    public RwsBeanConverter(RwsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object toJSON(RwsObject obj, Object value) throws RwsException {
        JSONObject result = new JSONObject();
        Set<String> names = obj.listPropertyNames();
        for (String name : names) {
            PropertyDescriptor prop = obj.getTargetProperty(name);
            try {
                Object propVal = prop.getReadMethod().invoke(value);
                Object convPropVal = registry.convertToJSON(propVal);
                result.put(prop.getName(), convPropVal);
            } catch (IllegalAccessException ex) {
                throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
            } catch (IllegalArgumentException ex) {
                throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
            } catch (InvocationTargetException ex) {
                throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
            }
        }
        return result;
    }

    @Override
    public Object fromJSON(RwsObject obj, Object value) throws RwsException {
        Object result;
        JSONObject val = (JSONObject) value;
        try {
            result = obj.getTargetClass().newInstance();
            Set<String> names = obj.listPropertyNames();
            for (String name : names) {
                PropertyDescriptor prop = obj.getTargetProperty(name);
                if (prop.getWriteMethod() != null) {
                    try {
                        Object propVal = val.get(prop.getName());
                        Object convPropVal = registry.convertFromJSON(propVal, prop.getPropertyType());
                        prop.getWriteMethod().invoke(result, convPropVal);
                    } catch (IllegalAccessException ex) {
                        throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
                    } catch (IllegalArgumentException ex) {
                        throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
                    } catch (InvocationTargetException ex) {
                        throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
                    }
                }
            }
        } catch (InstantiationException ex) {
            throw new RwsException("Could not convert value", ex);
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not convert value", ex);
        }
        return result;
    }

    @Override
    public void generateTypeScript(RwsObject obj, String instanceName, PrintWriter out) throws RwsException {
        String objectName = obj.scriptName();
        Class type = obj.getTargetClass();
        out.println("if (typeof " + objectName + " != 'function') {");
        out.println("    function " + objectName + "(id) {");
        out.println("        this.$class = '" + objectName + "';");
        out.println("        this.$id = id;");
        try {
            BeanInfo info = Introspector.getBeanInfo(type);
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            generateTypeProperties(props, type, out, true);
        } catch (IntrospectionException ex) {
            throw new RwsException("Could not generate type script", ex);
        }
        out.println("    }");
        generateInheritance(objectName, type, out, true);
        generateBody(obj, out);
        out.println("}");
        generateInstances(obj, out);
    }

    private void generateTypeProperties(PropertyDescriptor[] props, Class type, PrintWriter out, boolean first) {
        if (type.getSuperclass() != null) {
            RwsObject obj = registry.matchObject(type.getSuperclass());
            if (obj == null) {
                generateTypeProperties(props, type.getSuperclass(), out, false);
            }
        }
        for (PropertyDescriptor prop : props) {
            if (!prop.getName().equals("class")) {
                Method m = prop.getReadMethod();
                if (m != null && m.getDeclaringClass() == type) {
                    out.print("        this." + prop.getName() + " = ");
                    if (Number.class.isAssignableFrom(type)) {
                        out.println("0;");
                    } else {
                        out.println("null;");
                    }
                }
            }
        }
    }

    private void generateInheritance(String name, Class type, PrintWriter out, boolean first) {
        if (type.getSuperclass() != null) {
            RwsObject superobj = registry.matchObject(type.getSuperclass());
            if (superobj == null) {
                generateInheritance(name, type.getSuperclass(), out, false);
            } else {
                String superName = superobj.scriptName();
                out.println("    " + name + ".prototype = new " + superName + "();");
                out.println("    " + name + ".prototype.constructor = " + name + ";");
            }
        }
    }

    private void generateBody(RwsObject obj, PrintWriter out) throws RwsException {
        String name = obj.scriptName();
        List<Class> paramTypes = new ArrayList<Class>();
        for (String methodName : obj.listMethodNames()) {
            MethodDescriptor m = obj.getTargetMethod(methodName);
            addParamTypes(paramTypes, m.getMethod().getParameterTypes());
            String params = generateParameters(m.getMethod().getParameterTypes());
            if (params.length() > 0) {
                out.println("    " + name + ".prototype." + methodName + " = function(" + params + ", onsuccess, onfailure) {");
                out.println("        rws.call('sys', '" + methodName + "', this.$id, onsuccess, onfailure, " + params + ")");
                out.println("    }");
            } else {
                out.println("    " + name + ".prototype." + methodName + " = function(onsuccess, onfailure) {");
                out.println("        rws.call('sys', '" + methodName + "', this.$id, onsuccess, onfailure)");
                out.println("    }");
            }
        }

        for (String eventName : obj.listEventNames()) {
            EventSetDescriptor es = obj.getTargetEvent(eventName);
            String evnm = Strings.upperFirst(eventName);
            MethodDescriptor[] listenerMethods = es.getListenerMethodDescriptors();
            for (MethodDescriptor m : listenerMethods) {
                String mnm = Strings.upperFirst(m.getName());
                addParamTypes(paramTypes, m.getMethod().getParameterTypes());
                // Event subscribe
                out.println("    " + name + ".prototype.subscribe" + evnm + mnm + " = function(handler) {");
                out.println("        return rws.subscribe('sys', '" + m.getName() + "', '" + eventName + "', this.$id, handler)");
                out.println("    }");
                // Event unsubscribe
                out.println("    " + name + ".prototype.unsubscribe" + evnm + mnm + " = function(handlerid) {");
                out.println("        rws.unsubscribe(handlerid)");
                out.println("    }");
            }
        }

        for (Class paramType : paramTypes) {
            if (!paramType.getName().startsWith("java.lang.")
            && !java.util.Map.class.isAssignableFrom(paramType)
            && !java.util.Collection.class.isAssignableFrom(paramType)
            && !paramType.isArray()) {
                if (registry.matchObject(paramType) != null) {
                    out.println("    // DEPENDS ON: " + paramType.getName() + " (import available)");
                } else {
                    out.println("    // DEPENDS ON: " + paramType.getName());
                }
            }
//            RwsRegistry.generateTypeScript(paramType, out);
        }
    }

    private String generateParameters(Class[] types) throws RwsException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append("p");
            result.append(i);
        }
        return result.toString();
    }

    private void addParamTypes(List<Class> paramTypes, Class... types) {
        for (Class t : types) {
            if (!paramTypes.contains(t)) {
                if (t.getSuperclass() != null) {
                    addParamTypes(paramTypes, t.getSuperclass());
                }
                paramTypes.add(t);
            }
        }
    }

    private void generateInstances(RwsObject obj, PrintWriter out) {
        out.println("// INSTANCES:");
        Set<String> names = registry.getInstanceNames(obj.scriptName());
        for (String name : names) {
             out.println("if (!" + name + ") var " + name + " = new " + obj.scriptName() + "('" + name + "');");
        }
    }

}
