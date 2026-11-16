package org.intermine.util;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatcher;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isFinal;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import org.intermine.metadata.StringUtil;
import org.intermine.metadata.TypeUtil;
import org.intermine.metadata.Util;
import org.intermine.model.DynamicObject;
import org.intermine.model.FastPathObject;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.intermine.NotXmlRenderer;
import org.intermine.objectstore.proxy.ProxyReference;

/**
 * Holds the field values of an object that belongs to more than one model class (a Company
 * that is also Broke, a CEO that is also Broke), and creates the classes of such objects.
 *
 * A generated class extends the concrete model class, if there is one (java.lang.Object
 * otherwise), implements the model interfaces and org.intermine.model.DynamicObject, and has
 * one field: this handler, which its public no-argument constructor creates. Every method
 * of the model classes, and equals, hashCode and toString, is sent to
 * {@link #invoke(Object, Method, Object[])}. The classes are generated with Byte Buddy and
 * loaded into a class loader of their own (a child of the loader of the model classes), so
 * no JDK internals are opened (cglib needed --add-opens java.base/java.lang on JDK 16+).
 *
 * @author Andrew Varley
 */
public class DynamicBean implements InvocationHandler
{
    /** The field of a generated class that holds its DynamicBean. */
    private static final String HANDLER_FIELD = "imDynamicBean";
    private static final Object[] NO_ARGS = new Object[0];
    private static final Map<List<Class<?>>, Class<? extends FastPathObject>> CLASSES
        = new HashMap<List<Class<?>>, Class<? extends FastPathObject>>();

    //private static final Logger LOG = Logger.getLogger(DynamicBean.class);
    private Map<String, Object> map = new HashMap<String, Object>();

    /**
     * Construct the handler
     */
    public DynamicBean() {
        // empty
    }

    /**
     * Create an object that extends a class and implements interfaces, with a new DynamicBean
     * holding its fields.
     *
     * @param clazz the class to extend, or null
     * @param inter the interfaces to implement, or null
     * @return the new object
     */
    public static FastPathObject create(Class<? extends FastPathObject> clazz, Class<?> [] inter) {
        try {
            return createClass(clazz, inter).newInstance();
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Problem running constructor", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Problem running constructor", e);
        }
    }

    /**
     * Returns the class of the objects that extend a class and implement interfaces. Each
     * combination is generated once. Class.newInstance() on the returned class gives a working
     * object (its constructor creates the DynamicBean).
     *
     * @param clazz the class to extend, or null
     * @param inter the interfaces to implement, or null
     * @return the generated class, which also implements DynamicObject
     */
    public static synchronized Class<? extends FastPathObject> createClass(
            Class<? extends FastPathObject> clazz, Class<?>[] inter) {
        if ((clazz != null) && clazz.isInterface()) {
            throw new IllegalArgumentException("clazz must not be an interface");
        }
        Class<?> superclass = (clazz == null) ? Object.class : clazz;
        // a class name and a stable key, whatever order the interfaces come in
        Set<Class<?>> sorted = new TreeSet<Class<?>>(new ClassNameComparator());
        if (inter != null) {
            sorted.addAll(Arrays.asList(inter));
        }
        sorted.remove(DynamicObject.class);
        List<Class<?>> key = new ArrayList<Class<?>>();
        key.add(superclass);
        key.addAll(sorted);
        Class<? extends FastPathObject> retval = CLASSES.get(key);
        if (retval == null) {
            retval = generate(superclass, new ArrayList<Class<?>>(sorted));
            CLASSES.put(key, retval);
        }
        return retval;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends FastPathObject> generate(Class<?> superclass,
            List<Class<?>> interfaces) {
        List<Class<?>> all = new ArrayList<Class<?>>(interfaces);
        all.add(DynamicObject.class);
        // the model methods, and equals/hashCode/toString; not the other methods of Object
        // (finalize, clone) and not the final ones
        ElementMatcher.Junction<MethodDescription> handled = not(isDeclaredBy(Object.class))
            .or(isEquals()).or(isHashCode()).or(isToString());
        handled = handled.and(not(isDeclaredBy(DynamicObject.class))).and(not(isFinal()));
        List<Class<?>> loaders = new ArrayList<Class<?>>(all);
        loaders.add(superclass);
        loaders.add(DynamicBean.class);
        ClassLoader parent = new MultipleParentClassLoader.Builder()
            .appendMostSpecific(loaders).build();
        try {
            Class<?> loaded = new ByteBuddy()
                .subclass(superclass, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                .name(className(superclass, interfaces))
                .implement(all)
                .defineField(HANDLER_FIELD, InvocationHandler.class, Visibility.PRIVATE)
                .defineConstructor(Visibility.PUBLIC)
                .intercept(MethodCall.invoke(superclass.getConstructor())
                        .andThen(MethodCall.construct(DynamicBean.class.getConstructor())
                            .setsField(named(HANDLER_FIELD))))
                .method(handled)
                .intercept(InvocationHandlerAdapter.toField(HANDLER_FIELD))
                .method(isDeclaredBy(DynamicObject.class))
                .intercept(FieldAccessor.ofField(HANDLER_FIELD))
                .make()
                .load(parent, ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
            return (Class<? extends FastPathObject>) loaded;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot create a class from " + superclass.getName()
                    + ", which has no public no-argument constructor", e);
        }
    }

    /**
     * The name of a generated class: the package of the first model class, then the simple
     * names of the model classes in name order, for example
     * org.intermine.model.testmodel.Broke_Company$$Dynamic. Util.getFriendlyName and
     * DynamicUtil.getSimpleClassName do not read it; it is for stack traces and logs.
     */
    private static String className(Class<?> superclass, List<Class<?>> interfaces) {
        Set<Class<?>> parts = new TreeSet<Class<?>>(new ClassNameComparator());
        parts.addAll(interfaces);
        if (superclass != Object.class) {
            parts.add(superclass);
        }
        StringBuilder name = new StringBuilder();
        for (Class<?> part : parts) {
            if (name.length() == 0) {
                String pkg = part.getName().substring(0, part.getName().length()
                        - TypeUtil.unqualifiedName(part.getName()).length());
                // a package of the JDK cannot take new classes
                name.append(pkg.startsWith("java.") ? "org.intermine.model." : pkg);
            } else {
                name.append('_');
            }
            name.append(TypeUtil.unqualifiedName(part.getName()));
        }
        if (name.length() == 0) {
            name.append("org.intermine.model.Object");
        }
        return name.append("$$Dynamic").toString();
    }

    private static class ClassNameComparator implements java.util.Comparator<Class<?>>
    {
        @Override
        public int compare(Class<?> a, Class<?> b) {
            return a.getName().compareTo(b.getName());
        }
    }

    /**
     * Handles every method call of the model classes on the object, operating on the map.
     * Final methods (eg. getClass) are not sent here.
     *
     * @param obj the object
     * @param method the method called
     * @param arguments the parameters (null for a method without parameters)
     * @return the return value of the method call (0 or false for a primitive that is unset)
     * @throws Throwable if an error occurs in executing the method
     */
    @Override
    public Object invoke(Object obj, Method method, Object[] arguments) throws Throwable {
        Object retval = handle(obj, method, (arguments == null) ? NO_ARGS : arguments);
        if ((retval == null) && method.getReturnType().isPrimitive()) {
            return primitiveDefault(method.getReturnType());
        }
        return retval;
    }

    private static Object primitiveDefault(Class<?> type) {
        if (Boolean.TYPE.equals(type)) {
            return Boolean.FALSE;
        } else if (Short.TYPE.equals(type)) {
            return Short.valueOf((short) 0);
        } else if (Integer.TYPE.equals(type)) {
            return Integer.valueOf(0);
        } else if (Long.TYPE.equals(type)) {
            return Long.valueOf(0L);
        } else if (Float.TYPE.equals(type)) {
            return Float.valueOf(0.0f);
        } else if (Double.TYPE.equals(type)) {
            return Double.valueOf(0.0);
        } else if (Byte.TYPE.equals(type)) {
            return Byte.valueOf((byte) 0);
        } else if (Character.TYPE.equals(type)) {
            return Character.valueOf((char) 0);
        }
        // void
        return null;
    }

    private Object handle(Object obj, Method method, Object[] args) throws Throwable {
        // java.lang.Object methods
        if ("equals".equals(method.getName())) {
            if (args[0] instanceof InterMineObject) {
                Integer otherId = ((InterMineObject) args[0]).getId();
                Integer thisId = (Integer) map.get("id");
                return Boolean.valueOf(thisId != null ? thisId.equals(otherId) : obj == args[0]);
            }
            return Boolean.FALSE;
        }
        if ("hashCode".equals(method.getName())) {
            return map.get("id");
        }
        if ("toString".equals(method.getName())) {
            return doToString(obj);
        }
        if ("getoBJECT".equals(method.getName()) && (args.length == 0)) {
            return NotXmlRenderer.render(obj);
        }
        if ("getFieldValue".equals(method.getName()) && (args.length == 1)) {
            return handleGetFieldValue(obj, method, args);
        }
        if ("getFieldProxy".equals(method.getName()) && (args.length == 1)) {
            return handleGetFieldProxy(obj, args);
        }
        if ("setFieldValue".equals(method.getName()) && (args.length == 2)
                && (method.getReturnType() == Void.TYPE)) {
            String fieldName = (String) args[0];
            map.put(fieldName, args[1]);
            return null;
        }
        if ("addCollectionElement".equals(method.getName()) && (args.length == 2)
                && (method.getReturnType() == Void.TYPE)) {
            String fieldName = (String) args[0];
            @SuppressWarnings("unchecked") Collection<Object> col = (Collection<Object>) map
                .get(fieldName);
            if (col == null) {
                col = new HashSet<Object>();
                map.put(fieldName, col);
            }
            col.add(args[1]);
            return null;
        }
        if ("getFieldType".equals(method.getName()) && (args.length == 1)) {
            try {
                String methodName = "get" + StringUtil.reverseCapitalisation((String) args[0]);
                Method getMethod = obj.getClass().getMethod(methodName);
                return getMethod.getReturnType();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No such field " + args[0], e);
            }
        }
        if ("getElementType".equals(method.getName()) && (args.length == 1)) {
            String methodName = "add" + StringUtil.reverseCapitalisation((String) args[0]);
            Method[] methods = obj.getClass().getMethods();
            for (Method addMethod : methods) {
                if (addMethod.getName().equals(methodName)) {
                    return addMethod.getParameterTypes()[0];
                }
            }
            throw new RuntimeException("No such collection " + args[0]);
        }
        // Bean methods
        if (method.getName().startsWith("get") && (args.length == 0)) {
            return handleGet(method);
        }
        if (method.getName().startsWith("is")
            && (args.length == 0)) {
            return map.get(StringUtil.reverseCapitalisation(method.getName().substring(2)));
        }
        if (method.getName().startsWith("set") && (args.length == 1)
                && (method.getReturnType() == Void.TYPE)) {
            map.put(StringUtil.reverseCapitalisation(method.getName().substring(3)), args[0]);
            return null;
        }
        if (method.getName().startsWith("proxy") && (args.length == 1)
                && (method.getReturnType() == Void.TYPE)) {
            map.put(StringUtil.reverseCapitalisation(method.getName().substring(5)), args[0]);
            return null;
        }
        if (method.getName().startsWith("proxGet") && (args.length == 0)) {
            return map.get(StringUtil.reverseCapitalisation(method.getName().substring(7)));
        }
        if (method.getName().startsWith("add") && (args.length == 1)
                && (method.getReturnType() == Void.TYPE)) {
            return handleAddObject(method, args);
        }
        // setoBJECT: NotXmlParser fills the map of a dynamic object directly
        throw new UnsupportedOperationException(method.getName() + " is not supported on an object"
                + " of more than one class (" + Util.getFriendlyName(obj.getClass()) + ")");
    }

    private Object handleGetFieldValue(Object obj, Method method, Object[] args) {
        String fieldName = (String) args[0];
        Object retval = map.get(fieldName);
        if (retval instanceof ProxyReference) {
            try {
                retval = ((ProxyReference) retval).getObject();
            } catch (NullPointerException e) {
                NullPointerException e2 = new NullPointerException("Exception while calling "
                        + method.getName() + "(\"" + args[0] + "\") on object with ID "
                        + map.get("id"));
                e2.initCause(e);
                throw e2;
            } catch (Exception e) {
                RuntimeException e2 = new RuntimeException("Exception while calling "
                        + method.getName() + "(\"" + args[0] + "\") on object with ID "
                        + map.get("id"));
                e2.initCause(e);
                throw e2;
            }
        }
        if (retval == null) {
            Class<?> fieldType = null;
            try {
                String methodName = "get" + StringUtil.reverseCapitalisation((String) args[0]);
                Method getMethod = obj.getClass().getMethod(methodName);
                fieldType = getMethod.getReturnType();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No such field " + args[0], e);
            }

            if (Collection.class.isAssignableFrom(fieldType)) {
                retval = new HashSet<Object>();
                map.put(fieldName, retval);
            }
            if (fieldType.isPrimitive()) {
                if (Boolean.TYPE.equals(fieldType)) {
                    retval = Boolean.FALSE;
                } else if (Short.TYPE.equals(fieldType)) {
                    retval = new Short((short) 0);
                } else if (Integer.TYPE.equals(fieldType)) {
                    retval = new Integer(0);
                } else if (Long.TYPE.equals(fieldType)) {
                    retval = new Long(0);
                } else if (Float.TYPE.equals(fieldType)) {
                    retval = new Float(0.0);
                } else if (Double.TYPE.equals(fieldType)) {
                    retval = new Double(0.0);
                }
                map.put(fieldName, retval);
            }
        }
        return retval;
    }

    private Object handleGetFieldProxy(Object obj, Object[] args) {
        String fieldName = (String) args[0];
        Object retval = map.get(fieldName);
        if (retval == null) {
            Class<?> fieldType = null;
            try {
                String methodName = "get" + StringUtil.reverseCapitalisation((String) args[0]);
                Method getMethod = obj.getClass().getMethod(methodName);
                fieldType = getMethod.getReturnType();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No such field " + args[0], e);
            }

            if (Collection.class.isAssignableFrom(fieldType)) {
                retval = new HashSet<Object>();
                map.put(fieldName, retval);
            }
            if (fieldType.isPrimitive()) {
                if (Boolean.TYPE.equals(fieldType)) {
                    retval = Boolean.FALSE;
                } else if (Short.TYPE.equals(fieldType)) {
                    retval = new Short((short) 0);
                } else if (Integer.TYPE.equals(fieldType)) {
                    retval = new Integer(0);
                } else if (Long.TYPE.equals(fieldType)) {
                    retval = new Long(0);
                } else if (Float.TYPE.equals(fieldType)) {
                    retval = new Float(0.0);
                } else if (Double.TYPE.equals(fieldType)) {
                    retval = new Double(0.0);
                }
                map.put(fieldName, retval);
            }
        }
        return retval;
    }

    private Object handleGet(Method method) {
        Object retval = map.get(StringUtil.reverseCapitalisation(method.getName()
                    .substring(3)));
        if (retval instanceof ProxyReference) {
            try {
                retval = ((ProxyReference) retval).getObject();
            } catch (NullPointerException e) {
                NullPointerException e2 = new NullPointerException("Exception while calling "
                        + method.getName() + " on object with ID " + map.get("id"));
                e2.initCause(e);
                throw e2;
            } catch (Exception e) {
                RuntimeException e2 = new RuntimeException("Exception while calling "
                        + method.getName() + " on object with ID " + map.get("id"));
                e2.initCause(e);
                throw e2;
            }
        }
        if ((retval == null) && Collection.class.isAssignableFrom(method.getReturnType())) {
            retval = new HashSet<Object>();
            map.put(StringUtil.reverseCapitalisation(method.getName().substring(3)), retval);
        }
        return retval;
    }

    private Object handleAddObject(Method method, Object[] args) {
        String key = StringUtil.reverseCapitalisation(method.getName().substring(3));
        @SuppressWarnings("unchecked")
        Collection<Object> col = (Collection<Object>) map.get(key);
        if (col == null) {
            col = new HashSet<Object>();
            map.put(key, col);
        }
        col.add(args[0]);
        return null;
    }

    private String doToString(Object obj) {
        StringBuffer className = new StringBuffer();
        boolean needComma = false;
        Set<Class<?>> classes = Util.decomposeClass(obj.getClass());
        for (Class<?> clazz : classes) {
            if (needComma) {
                className.append(",");
            }
            needComma = true;
            className.append(TypeUtil.unqualifiedName(clazz.getName()));
        }
        StringBuffer retval = new StringBuffer(className.toString() + " [");
        Map<String, Object> sortedMap = new TreeMap<String, Object>(map);
        needComma = false;
        for (Map.Entry<String, Object> mapEntry : sortedMap.entrySet()) {
            String fieldName = mapEntry.getKey();
            Object fieldValue = mapEntry.getValue();
            if (!(fieldValue instanceof Collection<?>)) {
                if (needComma) {
                    retval.append(", ");
                }
                needComma = true;
                if (fieldValue instanceof ProxyReference) {
                    retval.append(fieldName + "=" + ((ProxyReference) fieldValue).getId());
                } else if (fieldValue instanceof InterMineObject) {
                    retval.append(fieldName + "=" + ((InterMineObject) fieldValue).getId());
                } else {
                    retval.append(fieldName + "=\"" + fieldValue + "\"");
                }
            }
        }
        return retval.toString() + "]";
    }

    /**
     * Getter for the map, for testing purposes
     *
     * @return a map of data for this object
     */
    public Map<String, Object> getMap() {
        return map;
    }
}
