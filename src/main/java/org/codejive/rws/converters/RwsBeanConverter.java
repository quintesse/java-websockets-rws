
package org.codejive.rws.converters;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.codejive.rws.RwsConverter;
import org.codejive.rws.RwsException;
import org.codejive.rws.RwsRegistry;
import org.json.simple.JSONObject;

/**
 *
 * @author tako
 */
public class RwsBeanConverter implements RwsConverter<Object> {
    private Set<String> names = new HashSet<String>();
    private boolean include;

    public RwsBeanConverter() {
        include = false;
    }

    public RwsBeanConverter(Collection<String> names, boolean include) {
        if (names != null) {
            this.names.addAll(names);
        }
        this.include = include;
    }

    public RwsBeanConverter(String[] names, boolean include) {
        if (names != null) {
            this.names.addAll(Arrays.asList(names));
        }
        this.include = include;
    }

    @Override
    public Object toJSON(Object value) throws RwsException {
        JSONObject result = new JSONObject();
        try {
            BeanInfo info = Introspector.getBeanInfo(value.getClass());
            for (PropertyDescriptor prop : info.getPropertyDescriptors()) {
                boolean contains =  names.contains(prop.getName());
                if ((include && contains) || (!include && !contains)) {
                    try {
                        Object propVal = prop.getReadMethod().invoke(value);
                        Object convPropVal = RwsRegistry.convertToJSON(propVal);
                        result.put(prop.getName(), convPropVal);
                    } catch (IllegalAccessException ex) {
                        throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
                    } catch (IllegalArgumentException ex) {
                        throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
                    } catch (InvocationTargetException ex) {
                        throw new RwsException("Could not convert property '" + prop.getName() + "'", ex);
                    }
                }
            }
        } catch (IntrospectionException ex) {
            throw new RwsException("Could not convert value", ex);
        }
        return result;
    }

    @Override
    public Object fromJSON(Object value, Class targetType) throws RwsException {
        Object result;
        JSONObject val = (JSONObject) value;
        try {
            BeanInfo info = Introspector.getBeanInfo(targetType);
            result = targetType.newInstance();
            for (PropertyDescriptor prop : info.getPropertyDescriptors()) {
                if (prop.getWriteMethod() != null) {
                    boolean contains =  names.contains(prop.getName());
                    if ((include && contains) || (!include && !contains)) {
                        try {
                            Object propVal = val.get(prop.getName());
                            Object convPropVal = RwsRegistry.convertFromJSON(propVal, prop.getPropertyType());
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
            }
        } catch (InstantiationException ex) {
            throw new RwsException("Could not convert value", ex);
        } catch (IllegalAccessException ex) {
            throw new RwsException("Could not convert value", ex);
        } catch (IntrospectionException ex) {
            throw new RwsException("Could not convert value", ex);
        }
        return result;
    }

    @Override
    public void generateTypeScript(Class<Object> type, PrintWriter out) throws RwsException {
        out.println("if (typeof " + type.getSimpleName() + " != 'function') {");
        out.println("    function " + type.getSimpleName() + "() {");
        try {
            BeanInfo info = Introspector.getBeanInfo(type);
            for (PropertyDescriptor prop : info.getPropertyDescriptors()) {
                if (prop.getWriteMethod() != null) {
                    out.print("        this." + prop.getName() + " = ");
                    if (Number.class.isAssignableFrom(type)) {
                        out.println("0;");
                    } else {
                        out.println("null;");
                    }
                }
            }
        } catch (IntrospectionException ex) {
            throw new RwsException("Could not generate type script", ex);
        }
        out.println("    }");
        out.println("}");
    }

}