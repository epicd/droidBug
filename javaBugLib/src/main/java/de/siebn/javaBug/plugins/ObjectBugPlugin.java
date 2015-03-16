package de.siebn.javaBug.plugins;

import de.siebn.javaBug.JavaBug;
import de.siebn.javaBug.NanoHTTPD;
import de.siebn.javaBug.objectOut.OutputCategory;
import de.siebn.javaBug.typeAdapter.TypeAdapters;
import de.siebn.javaBug.util.XML;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created by Sieben on 05.03.2015.
 */
public class ObjectBugPlugin implements RootBugPlugin.MainBugPlugin {
    public final HashMap<Integer, Object> references = new HashMap<>();
    private ArrayList<Object> rootObjects = new ArrayList<>();

    private final HashMap<Class<?>, AllClassMembers> allMembersMap = new HashMap<>();
    private JavaBug javaBug;

    public class AllClassMembers {
        public ArrayList<Field> fields = new ArrayList<>();
        public ArrayList<Method> methods = new ArrayList<>();
        private AllClassMembers(Class<?> clazz) {
            addAllMembers(clazz);
            Collections.sort(fields, new Comparator<Field>() {
                @Override
                public int compare(Field o1, Field o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            Collections.sort(methods, new Comparator<Method>() {@Override public int compare(Method o1, Method o2) {return o1.getName().compareTo(o2.getName());}});
        }

        private void addAllMembers(Class<?> clazz) {
            Collections.addAll(fields, clazz.getDeclaredFields());
            Collections.addAll(methods, clazz.getDeclaredMethods());
            if (clazz.getSuperclass() != null) addAllMembers(clazz.getSuperclass());
        }
    }

    public List<OutputCategory> getOutputCategories(Class<?> clazz) {
        ArrayList<OutputCategory> outputCategories = new ArrayList<>();
        for (OutputCategory oc : javaBug.getPlugins(OutputCategory.class))
            if (oc.canOutputClass(clazz))
                outputCategories.add(oc);
        return outputCategories;
    }

    public AllClassMembers getAllMembers(Class<?> clazz) {
        AllClassMembers allMembers = allMembersMap.get(clazz);
        if (allMembers == null)
            allMembersMap.put(clazz, allMembers = new AllClassMembers(clazz));
        return allMembers;
    }

    public ObjectBugPlugin(JavaBug javaBug) {
        this.javaBug = javaBug;
    }

    public void addRootObject(Object object) {
        rootObjects.add(object);
    }

    @Override
    public String getTabName() {
        return "Objects";
    }

    @Override
    public String getUrl() {
        return "/objects/";
    }

    @Override
    public String getTagClass() {
        return "objects";
    }

    @Override
    public int getPriority() {
        return 1000;
    }

    @JavaBug.Serve("^/objects/")
    public String serveObjects() {
        XML ul = new XML("ul");
        for (Object o : rootObjects) {
            addObjectInfo(ul.add("li").setClass("object"), o, null, null);
        }
        return ul.getXml();
    }

    @JavaBug.Serve("^/objectDetails/([^/]*)")
    public String serveObjectDetails(String[] params) {
        try {
            int hash = Integer.parseInt(params[1], 16);
            Object o = references.get(hash);
            return getObjectDetails(o, null);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    @JavaBug.Serve("^/objectDetails/([^/]*)/([^/]*)")
    public String serveObjectDetailsType(String[] params) {
        try {
            int hash = Integer.parseInt(params[2], 16);
            Object o = references.get(hash);
            return getObjectDetails(o, params[1]);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public String getObjectDetails(Object o, String type) {
        XML ul = new XML("ul");

        for (OutputCategory oc : javaBug.getObjectBug().getOutputCategories(o.getClass())) {
            if (oc.getType().equals(type)) {
                oc.add(ul, o);
                return ul.getXml();
            }
        }

        for (OutputCategory oc : javaBug.getObjectBug().getOutputCategories(o.getClass())) {
            String name = oc.getName();
            if (name != null) {
                XML ocul = ul.add("li").setAttr("expand", javaBug.getObjectBug().getObjectDetailsLink(o, oc.getType())).appendText(name);
                if (oc.opened()) {
                    XML expand = ocul.add("ul").setClass("expand");
                    oc.add(expand, o);
                }
            }
        }
        return ul.getXml();
    }

    public String getObjectReference(Object o) {
        String hexHash = Integer.toHexString(System.identityHashCode(o));
        references.put(System.identityHashCode(o), o);
        return hexHash;
    }

    public String getObjectDetailsLink(Object o) {
        return "/objectDetails/" + getObjectReference(o);
    }

    public String getObjectDetailsLink(Object o, String type) {
        return "/objectDetails/" + type + "/" + getObjectReference(o);
    }

    @JavaBug.Serve("^/objectEdit/([^/]*)/([^/]*)")
    public String serveObjectEdit(NanoHTTPD.IHTTPSession session, String[] params) throws Exception {
        if (session.getMethod() == NanoHTTPD.Method.POST)
            session.parseBody(null);
        int hash = Integer.parseInt(params[1], 16);
        String fieldName = params[2];
        Object o = references.get(hash);
        AllClassMembers allMembers = getAllMembers(o.getClass());
        for (Field f : allMembers.fields) {
            if (f.getName().equals(fieldName)) {
                TypeAdapters.TypeAdapter<?> adapter = TypeAdapters.getTypeAdapter(f.getType());
                if (adapter == null) throw new JavaBug.ExceptionResult(NanoHTTPD.Response.Status.BAD_REQUEST, "No TypeAdapter found!");
                Object val;
                try {
                    String v = session.getParms().get("o");
                    val = adapter.parse((Class) f.getType(), v == null ? null : v);
                } catch (Exception e) {
                    throw new JavaBug.ExceptionResult(NanoHTTPD.Response.Status.BAD_REQUEST, "Could not parse \"" + session.getParms().get("o") + "\"");
                }
                f.set(o, val);
                return String.valueOf(f.get(o));
            }
        }
        return "ERROR";
    }

    @JavaBug.Serve("^/invoke/([^/]*)/([^/]*)")
    public String serveInvoke(NanoHTTPD.IHTTPSession session, String[] params) throws Exception {
        int hash = Integer.parseInt(params[1], 16);
        String methodName = params[2];
        Object o = references.get(hash);
        AllClassMembers allMembers = getAllMembers(o.getClass());
        for (Method m : allMembers.methods) {
            if (m.getName().equals(methodName)) {
                Object r = m.invoke(o);
                return r == null ? "null" : getObjectDetails(m.invoke(o), "string");
            }
        }
        return "ERROR";
    }

    public void addObjectInfo(XML li, Object o, String parentHash, Field field) {
        if (o != null && (field == null || !field.getType().isPrimitive())) {
            li.setAttr("expand", getObjectDetailsLink(o));
        } else {
            li.addClass("notOpenable");
        }
        XML f = li.add("span").setClass("field");
        XML v = f.add("span").setClass("value");
        TypeAdapters.TypeAdapter<Object> adapter = TypeAdapters.getTypeAdapter(o == null ? Object.class : o.getClass());
        if (o != null && parentHash != null && field != null && !Modifier.isFinal(field.getModifiers()) && adapter.canParse(o.getClass())) {
            v.setAttr("editurl", "/objectEdit/" + parentHash + "/" + field.getName());
            if (!field.getType().isPrimitive())
                v.setAttr("editNullify", "true");
        }
        v.appendText(TypeAdapters.toString(o));
    }
}