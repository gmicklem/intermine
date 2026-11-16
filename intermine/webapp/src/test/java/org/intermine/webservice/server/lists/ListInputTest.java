package org.intermine.webservice.server.lists;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;

public class ListInputTest {

    private ListInput input(String name) {
        HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getParameter("name")).andReturn(name).anyTimes();
        replay(request);
        return new ListInput(request, null, null);
    }

    @Test
    public void temporaryListNameIsStablePerRequest() {
        ListInput input = input("l0");
        assertEquals("l0", input.getListName());
        assertEquals(input.getTemporaryListName(), input.getTemporaryListName());
        assertTrue(input.getTemporaryListName().startsWith("l0" + ListInput.TEMP_SUFFIX));
    }

    @Test
    public void temporaryListNamesOfConcurrentRequestsForOneListDiffer() {
        // two uploads of the same list name used to share l0_temp (im-jqz.57)
        assertFalse(input("l0").getTemporaryListName().equals(input("l0").getTemporaryListName()));
    }
}
