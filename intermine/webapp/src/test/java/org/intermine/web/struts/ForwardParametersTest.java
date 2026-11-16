package org.intermine.web.struts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.struts.action.ActionForward;
import org.junit.Test;

public class ForwardParametersTest {

    @Test
    public void parametersAreEncoded() {
        ActionForward forward = new ForwardParameters(new ActionForward("/results.do", true))
                .addParameter("trail", "|query").addParameter("name", "a b&c").forward();
        assertEquals("/results.do?trail=%7Cquery&name=a+b%26c", forward.getPath());
        assertTrue(forward.getRedirect());
    }

    /**
     * loadQuery.do?method=xml&skipBuilder=true without a trail parameter threw a
     * NullPointerException in URLEncoder.encode (im-jqz.62).
     */
    @Test
    public void parameterWithoutAValueIsLeftOut() {
        ActionForward forward = new ForwardParameters(new ActionForward("/results.do", true))
                .addParameter("trail", null).forward();
        assertEquals("/results.do", forward.getPath());

        forward = new ForwardParameters(new ActionForward("/results.do", false))
                .addParameter("trail", null).addParameter("page", "2").forward();
        assertEquals("/results.do?page=2", forward.getPath());
    }
}
