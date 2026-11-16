package org.intermine.web.struts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.struts.action.ActionForm;
import org.apache.struts.upload.FormFile;
import org.junit.Test;

/**
 * The Struts forms are session scoped (the default scope of an action mapping) and Tomcat can
 * persist the session over a redeploy. A class without a serialVersionUID gets one computed from
 * its members, which changes with any edit and then reads as "Cannot deserialize session
 * attribute ... InvalidClassException" (im-jqz.40).
 */
public class FormSerializationTest {

    @Test
    public void everyFormDeclaresASerialVersionUID() throws Exception {
        File dir = new File(TemplateForm.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        assertTrue("classes directory " + dir, dir.isDirectory());
        File[] classFiles = new File(dir, TemplateForm.class.getPackage().getName()
                .replace('.', '/')).listFiles();
        List<String> without = new ArrayList<String>();
        int forms = 0;
        for (File classFile : classFiles) {
            String name = classFile.getName();
            if (!name.endsWith(".class")) {
                continue;
            }
            Class<?> c = Class.forName(TemplateForm.class.getPackage().getName() + "."
                    + name.substring(0, name.length() - ".class".length()));
            if (!ActionForm.class.isAssignableFrom(c)) {
                continue;
            }
            forms++;
            try {
                Field f = c.getDeclaredField("serialVersionUID");
                assertTrue(c.getName(), Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers()));
            } catch (NoSuchFieldException e) {
                without.add(c.getName());
            }
        }
        assertTrue("found the forms", forms > 20);
        assertEquals("forms without serialVersionUID", new ArrayList<String>(), without);
    }

    @Test
    public void templateFormSurvivesSerialization() throws Exception {
        TemplateForm form = new TemplateForm();
        form.setAttributeValues("1", "x & y");
        form.setAttributeValues("2", new String[] {"a", "b"});
        form.setAttributeOps("1", "=");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(form);
        out.close();
        TemplateForm copy = (TemplateForm) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        assertEquals("x & y", copy.getAttributeValues("1"));
        assertArrayEquals(new String[] {"a", "b"}, (String[]) copy.getAttributeValues("2"));
        assertEquals("=", copy.getAttributeOps("1"));
    }

    @Test
    public void buildBagFormWithAnUploadSurvivesSerialization() throws Exception {
        BuildBagForm form = new BuildBagForm();
        form.setText("a b c");
        // an upload (FormFile) is not Serializable and not session state: a stand-in that
        // cannot be written would fail the serialization of a form that kept it
        form.setFormFile((FormFile) Proxy.newProxyInstance(FormFile.class.getClassLoader(),
                new Class<?>[] {FormFile.class}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return null;
                    }
                }));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(form);
        out.close();
        BuildBagForm copy = (BuildBagForm) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        assertEquals("a b c", copy.getText());
    }
}
