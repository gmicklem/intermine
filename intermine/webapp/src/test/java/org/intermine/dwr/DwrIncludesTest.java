package org.intermine.dwr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * dwr.xml exposes only the listed methods of AjaxServices (im-jqz.52): every method that the JSPs
 * and scripts call must be in the list (im-jqz.60: autocompleter.js called getContent, which was
 * not, and historyBagView.js called filterByTag, which AjaxServices has never had).
 */
public class DwrIncludesTest {

    private static final Pattern CALL = Pattern.compile("(?<![A-Za-z0-9_])AjaxServices\\.(\\w+)\\s*\\(");
    /** Commented out code: block comments, JSP and HTML comments, lines that start with //. */
    private static final Pattern COMMENT =
            Pattern.compile("(?s)/\\*.*?\\*/|<%--.*?--%>|<!--.*?-->|(?m)^\\s*//[^\\n]*$");
    private static final Pattern INCLUDE = Pattern.compile("<include\\s+method=\"(\\w+)\"");

    private static String read(File f) throws IOException {
        // FileInputStream, not Files.readAllBytes: relative paths are resolved against the working
        // directory of the process by java.io, but against the user.dir property by java.nio
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, n);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static void collect(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                // third party libraries are not ours
                if (!"vendor".equals(name)) {
                    collect(child, files);
                }
            } else if (name.endsWith(".js") || name.endsWith(".jsp") || name.endsWith(".tag")
                    || name.endsWith(".html")) {
                files.add(child);
            }
        }
    }

    /** The methods of the AjaxServices creator in dwr.xml. */
    private Set<String> includedMethods(File dwrXml) throws IOException {
        String xml = read(dwrXml);
        int start = xml.indexOf("javascript=\"AjaxServices\"");
        assertTrue("AjaxServices creator in dwr.xml", start >= 0);
        int end = xml.indexOf("</create>", start);
        Set<String> methods = new TreeSet<String>();
        Matcher m = INCLUDE.matcher(xml.substring(start, end));
        while (m.find()) {
            methods.add(m.group(1));
        }
        return methods;
    }

    @Test
    public void everyCalledMethodIsIncluded() throws Exception {
        File webapp = new File("src/main/webapp");
        assertTrue("run from the webapp project directory: " + webapp.getAbsolutePath(),
                webapp.isDirectory());
        File dwrXml = new File(webapp, "WEB-INF/dwr.xml");
        assertTrue(dwrXml.getAbsolutePath(), dwrXml.isFile());
        Set<String> included = includedMethods(dwrXml);
        assertTrue(included.contains("getContent"));
        assertTrue(included.contains("validateBagName"));

        List<File> roots = new ArrayList<File>();
        roots.add(webapp);
        File bioWebapp = new File("../../bio/webapp/src/main/webapp");
        if (bioWebapp.isDirectory()) {
            roots.add(bioWebapp);
        }
        List<File> files = new ArrayList<File>();
        for (File root : roots) {
            collect(root, files);
        }
        assertTrue("found the JSPs and scripts", files.size() > 100);

        List<String> missing = new ArrayList<String>();
        for (File f : files) {
            Matcher m = CALL.matcher(COMMENT.matcher(read(f)).replaceAll(""));
            while (m.find()) {
                if (!included.contains(m.group(1))) {
                    missing.add(f.getName() + ": AjaxServices." + m.group(1));
                }
            }
        }
        assertEquals("called but not in the include list of dwr.xml", new ArrayList<String>(),
                missing);
    }
}
