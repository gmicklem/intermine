<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<tiles:importAttribute/>
<%-- "short" is a Java keyword, so Tomcat 8.5 rejects it as an EL identifier; use the scope maps --%>
<c:set var="isShort" value="${pageScope['short'] == 'true' || requestScope['short'] == 'true'}"/>

<!-- tablePageLinks -->
<span class="tablePageLinks">
      <c:choose>
      <c:when test="${!resultsTable.firstPage}">
        <c:if test="${!isShort}">
          <html:link action="/${currentPage}?table=${param.table}&amp;bagName=${bagName}&amp;page=0&amp;size=${resultsTable.pageSize}&amp;trail=${param.trail}">
            &nbsp;&lt;&lt;&nbsp;<fmt:message key="results.first"/>
          </html:link>
        </c:if>
        <html:link action="/${currentPage}?table=${param.table}&amp;bagName=${bagName}&amp;page=${resultsTable.page-1}&amp;size=${resultsTable.pageSize}&amp;trail=${param.trail}">
          &nbsp;&lt;&nbsp;<fmt:message key="results.previous"/>
        </html:link><span style="float:left">&nbsp;|&nbsp;</span>
      </c:when>
      <c:otherwise>
        <span style="float:left"><c:if test="${!isShort}">&nbsp;&lt;&lt;&nbsp;<fmt:message key="results.first"/></c:if>
        &nbsp;&lt;&nbsp;<fmt:message key="results.previous"/>&nbsp;|&nbsp;</span>
      </c:otherwise>
    </c:choose>
    <c:choose>
      <c:when test="${!resultsTable.lastPage && (resultsTable.page+2)*resultsTable.pageSize < resultsTable.maxRetrievableIndex}">
        <html:link action="/${currentPage}?table=${param.table}&amp;bagName=${bagName}&amp;page=${resultsTable.page+1}&amp;size=${resultsTable.pageSize}&amp;trail=${param.trail}">
          <fmt:message key="results.next"/>&nbsp;&gt;&nbsp;
        </html:link>
      </c:when>
      <c:otherwise>
        <fmt:message key="results.next"/>&nbsp;&gt;&nbsp;
      </c:otherwise>
    </c:choose>
    <c:if test="${!isShort}">
    <c:choose>
      <c:when test="${!resultsTable.lastPage && resultsTable.maxRetrievableIndex > resultsTable.estimatedSize}">
        <html:link action="/changeTable?table=${param.table}&amp;method=last&amp;trail=${param.trail}&amp;currentPage=${currentPage}&amp;bagName=${bag.name}">
          <fmt:message key="results.last"/>&nbsp;&gt;&gt;&nbsp;
        </html:link>
      </c:when>
      <c:otherwise>
        <fmt:message key="results.last"/>&nbsp;&gt;&gt;&nbsp;
      </c:otherwise>
    </c:choose>
    </c:if>
</span>
<!-- /tablePageLinks.jsp -->
