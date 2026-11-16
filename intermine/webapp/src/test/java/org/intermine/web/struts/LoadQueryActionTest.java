package org.intermine.web.struts;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionMapping;
import org.junit.Test;

public class LoadQueryActionTest {

    /** A request without the query parameter is a 400, not a NullPointerException (500). */
    @Test
    public void testMissingQueryParameterIsBadRequest() throws Exception {
        HttpServletRequest request = createMock(HttpServletRequest.class);
        HttpServletResponse response = createMock(HttpServletResponse.class);
        expect(request.getParameter("query")).andReturn(null);
        response.sendError(eq(HttpServletResponse.SC_BAD_REQUEST),
                anyString());
        replay(request);
        replay(response);

        assertNull(new LoadQueryAction().xml(new ActionMapping(), null, request, response));

        verify(request);
        verify(response);
    }
}
