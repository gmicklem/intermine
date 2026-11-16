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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.intermine.metadata.TypeUtil;
import org.intermine.metadata.Util;
import org.intermine.model.DynamicObject;
import org.intermine.model.FastPathObject;
import org.intermine.model.testmodel.Broke;
import org.intermine.model.testmodel.CEO;
import org.intermine.model.testmodel.Company;
import org.intermine.model.testmodel.Department;
import org.intermine.model.testmodel.Employable;
import org.intermine.model.testmodel.Employee;
import org.intermine.model.testmodel.HasAddress;
import org.intermine.model.testmodel.HasSecretarys;
import org.intermine.model.testmodel.ImportantPerson;
import org.intermine.model.testmodel.Manager;
import org.intermine.model.testmodel.RandomInterface;
import org.intermine.model.testmodel.Thing;

public class DynamicUtilTest extends TestCase
{
    public DynamicUtilTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
    }

    // NEED MORE TESTS FOR MULTIPLE INHERITED INTERFACES WHEN AVAILABLE
    // eg.    - B
    //       /
    //      A     - D
    //       \   /   \
    //        - C     - F
    //           \   /
    //            - E

    public void testCreateObjectOneInterface() throws Exception {
        Object obj = DynamicUtil.createObject(Collections.singleton(Company.class));
        assertTrue(obj instanceof Company);
        Company c = (Company) obj;
        c.setName("Flibble");
        assertEquals("Flibble", c.getName());
    }

    public void testCreateObjectOneInterfaceWithParents() throws Exception {
        Object obj = DynamicUtil.createObject(Collections.singleton(Employable.class));
        assertTrue(obj instanceof Employable);
        assertTrue(obj instanceof Thing);
    }

    public void testCreateObjectNoClassTwoInterfaces() throws Exception {
        Set intSet = new HashSet();
        intSet.add(Company.class);
        intSet.add(Broke.class);
        Object obj = DynamicUtil.createObject(intSet);
        assertTrue(obj instanceof Company);
        assertTrue(obj instanceof RandomInterface);
        assertTrue(obj instanceof Broke);
        assertTrue(obj instanceof HasSecretarys);
        assertTrue(obj instanceof HasAddress);

        ((Company) obj).setName("Wotsit");
        ((Broke) obj).setDebt(40);

        assertEquals("Wotsit", ((Company) obj).getName());
        assertEquals(40, ((Broke) obj).getDebt());
    }

    public void testCreateObjectClassOnly() throws Exception {
        Object obj = DynamicUtil.createObject(Collections.singleton(Employee.class));
        assertEquals(Employee.class, obj.getClass());
    }

    public void testCreateObjectClassAndRedundantInterfaces() {
        Set intSet = new HashSet();
        intSet.add(Employee.class);
        intSet.add(Employable.class);
        Object obj = DynamicUtil.createObject(intSet);
        assertEquals(Employee.class, obj.getClass());
        assertTrue(obj instanceof Employee);
        assertTrue(obj instanceof Employable);
    }

    public void testCreateObjectClassInterfaces() throws Exception {
        Set intSet = new HashSet();
        intSet.add(Manager.class);
        intSet.add(Broke.class);
        Object obj = DynamicUtil.createObject(intSet);
        assertTrue(obj instanceof Manager);
        assertTrue(obj instanceof Employee);
        assertTrue(obj instanceof ImportantPerson);
        assertTrue(obj instanceof Employable);
        assertTrue(obj instanceof HasAddress);
        assertTrue(obj instanceof Broke);

        Manager m = (Manager) obj;
        m.setName("Frank");
        m.setTitle("Mr.");
        ((Broke) m).setDebt(30);
        assertEquals("Frank", m.getName());
        assertEquals("Mr.", m.getTitle());
        assertEquals(30, ((Broke) m).getDebt());
    }

    public void testCreateObjectTwoClasses() throws Exception {
        Set intSet = new HashSet();
        intSet.add(Manager.class);
        intSet.add(Department.class);
        try {
            Object obj = DynamicUtil.createObject(intSet);
            fail("Expected: IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testCreateObjectNothing() throws Exception {
        try {
            Object obj = DynamicUtil.createObject(Collections.EMPTY_SET);
            fail("Expected: IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testConstructors() throws Exception {
        Class c = DynamicUtil.composeClass(Company.class, Broke.class);
        // the constructor creates the DynamicBean
        Company obj = (Company) c.newInstance();
        obj.setName("Fred");
        assertEquals("Fred", obj.getName());
        assertTrue(obj instanceof DynamicObject);
        assertEquals("Fred", ((DynamicBean) ((DynamicObject) obj).dynamicHandler()).getMap().get("name"));
        // each object has its own
        Company obj2 = (Company) c.newInstance();
        assertNull(obj2.getName());
    }

    public void testComposeClassIsCached() throws Exception {
        Class c1 = DynamicUtil.composeClass(Company.class, Broke.class);
        Class c2 = DynamicUtil.composeClass(Broke.class, Company.class);
        assertSame(c1, c2);
        assertSame(c1, DynamicUtil.createObject(new HashSet(Arrays.asList(Broke.class, Company.class))).getClass());
        assertSame(c1, DynamicBean.createClass(null, new Class[] {Broke.class, Company.class}));
    }

    public void testShadowAndPlainClassesAreNotDynamic() throws Exception {
        assertFalse(DynamicUtil.createObject(Collections.singleton(Company.class)) instanceof DynamicObject);
        assertFalse(DynamicUtil.createObject(Collections.singleton(Employee.class)) instanceof DynamicObject);
    }

    public void testDynamicClassName() throws Exception {
        assertEquals("org.intermine.model.testmodel.Broke_Company$$Dynamic",
                DynamicUtil.composeClass(Company.class, Broke.class).getName());
        assertEquals("org.intermine.model.testmodel.Broke_CEO$$Dynamic",
                DynamicUtil.composeClass(CEO.class, Broke.class).getName());
    }

    // decomposeClass, getFriendlyName and the simple-class methods look at the classes a
    // dynamic class is made of, not at its generated name
    public void testDecomposeDynamicClass() throws Exception {
        Class c = DynamicUtil.composeClass(Company.class, Broke.class);
        assertEquals(new HashSet(Arrays.asList(Company.class, Broke.class)), Util.decomposeClass(c));
        assertEquals("Broke,Company", Util.getFriendlyName(c));

        Class ceo = DynamicUtil.composeClass(CEO.class, Broke.class);
        assertEquals(new HashSet(Arrays.asList(CEO.class, Broke.class)), Util.decomposeClass(ceo));
        assertEquals("Broke,CEO", Util.getFriendlyName(ceo));
        assertTrue(DynamicUtil.isAssignableFrom(Manager.class, ceo));
        assertTrue(DynamicUtil.isAssignableFrom(c, DynamicUtil.composeClass(Company.class, Broke.class)));
        assertFalse(DynamicUtil.isAssignableFrom(c, ceo));

        // a redundant interface is dropped: CEO is already an Employable
        Class ceo2 = DynamicUtil.composeClass(CEO.class, Broke.class, Employable.class);
        assertEquals(new HashSet(Arrays.asList(CEO.class, Broke.class)), Util.decomposeClass(ceo2));
    }

    public void testSimpleClassOfDynamicClassWithOneModelClass() throws Exception {
        // a dynamic class of one model class only (DynamicBean.create, no Shadow class used)
        Class c = DynamicBean.createClass(null, new Class[] {Company.class});
        assertEquals(Company.class, DynamicUtil.getSimpleClass(c));
        assertEquals(Company.class.getName(), DynamicUtil.getSimpleClassName(c));
        assertEquals("Company", Util.getFriendlyName(c));
        assertEquals("Company", DynamicUtil.getSimpleClass(c).getSimpleName());
    }

    public void testDynamicObjectMethods() throws Exception {
        Set classes = new HashSet(Arrays.asList(Company.class, Broke.class));
        Company c = (Company) DynamicUtil.createObject(classes);
        // primitives that were never set are 0, as with a plain object
        assertEquals(0, ((Broke) c).getDebt());
        assertEquals(0, c.getVatNumber());
        c.setId(Integer.valueOf(17));
        c.setName("Wotsit");
        ((Broke) c).setDebt(40);
        assertEquals("Broke,Company [debt=\"40\", id=\"17\", name=\"Wotsit\"]", c.toString());
        Company same = (Company) DynamicUtil.createObject(classes);
        same.setId(Integer.valueOf(17));
        assertEquals(c, same);
        assertEquals(c.hashCode(), same.hashCode());
        assertEquals("Wotsit", c.getFieldValue("name"));
        assertEquals(Integer.valueOf(40), c.getFieldValue("debt"));
        assertEquals(Integer.TYPE, c.getFieldType("debt"));
        assertEquals("Broke,Company:17", DynamicUtil.getFriendlyDesc(c));
        // the model fields of the class, and not the DynamicObject method
        assertEquals(new HashSet(Arrays.asList("address", "bank", "CEO", "contractors", "debt", "departments",
                "id", "interestRate", "name", "oldContracts", "owedBy", "secretarys", "vatNumber")),
                TypeUtil.getFieldInfos(c.getClass()).keySet());
    }

    public void testConcreteSuperclassDynamicObject() throws Exception {
        CEO ceo = (CEO) DynamicUtil.createObject(new HashSet(Arrays.asList(CEO.class, Broke.class)));
        ceo.setName("Fred");
        ceo.setSalary(100);
        ((Broke) ceo).setDebt(30);
        assertEquals("Fred", ceo.getName());
        assertEquals(100, ceo.getSalary());
        assertEquals(30, ((Broke) ceo).getDebt());
        assertTrue(ceo instanceof Manager);
        assertTrue(ceo instanceof DynamicObject);
        // the values are in the DynamicBean, not in the fields of CEO
        assertEquals("Fred", ((DynamicBean) ((DynamicObject) ceo).dynamicHandler()).getMap().get("name"));
    }

    public void testInstantiateObjectNullClassName() throws Exception {
        Object obj = DynamicUtil.instantiateObject(null, "org.intermine.model.testmodel.Broke");
        assertTrue(obj instanceof Broke);
    }

    public void testInstantiateObjectEmptyClassName() throws Exception {
        Object obj = DynamicUtil.instantiateObject("", "org.intermine.model.testmodel.Broke");
        assertTrue(obj instanceof Broke);
    }

    public void testInstantiateObjectNullImplementations() throws Exception {
        Object obj = DynamicUtil.instantiateObject("org.intermine.model.testmodel.Manager", null);
        assertTrue(obj instanceof Manager);
    }

    public void testInstantiateObjectEmptyImplementations() throws Exception {
        Object obj = DynamicUtil.instantiateObject("org.intermine.model.testmodel.Manager", "");
        assertTrue(obj instanceof Manager);
    }

    public void testInstantiateObject() throws Exception {
        Object obj = DynamicUtil.instantiateObject("org.intermine.model.testmodel.Manager", "org.intermine.model.testmodel.Broke");
        assertTrue(obj instanceof Manager);
        assertTrue(obj instanceof Broke);
    }

    public void testGetSimpleClass() throws Exception {
        FastPathObject obj = DynamicUtil.instantiateObject("org.intermine.model.testmodel.Company", null);
        assertEquals(Company.class, DynamicUtil.getSimpleClass(obj.getClass()));

        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        interfaces.add(Company.class);
        interfaces.add(Employee.class);
        obj = DynamicUtil.createObject(interfaces);

        // dynamic class composed of multiple classes should throw an exception
        try {
            DynamicUtil.getSimpleClass(obj);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testGetSimpleClassName() throws Exception {
        FastPathObject obj = DynamicUtil.instantiateObject("org.intermine.model.testmodel.Company", null);
        assertEquals(Company.class.getName(), DynamicUtil.getSimpleClassName(obj.getClass()));

        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        interfaces.add(Company.class);
        interfaces.add(Employee.class);
        obj = DynamicUtil.createObject(interfaces);

        // dynamic class composed of multiple classes should throw an exception
        try {
            DynamicUtil.getSimpleClassName(obj);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testClassHeirarchyBrokeCEOIsBroke() throws Exception {
        Class brokeCEO = DynamicUtil.composeClass(new HashSet(Arrays.asList(new Class[] {Broke.class, CEO.class})));
        assertTrue(Broke.class.isAssignableFrom(brokeCEO));
    }

    public void testClassHeirarchyBrokeCEOIsCEO() throws Exception {
        Class brokeCEO = DynamicUtil.composeClass(new HashSet(Arrays.asList(new Class[] {Broke.class, CEO.class})));
        assertTrue(CEO.class.isAssignableFrom(brokeCEO));
    }

    public void testGetNullPrimitives() throws Exception {
        Company c = (Company) DynamicUtil.createObject(Collections.singleton(Company.class));
        assertEquals(0, c.getVatNumber());
        assertEquals(Integer.valueOf(0), c.getFieldValue("vatNumber"));
    }
}
