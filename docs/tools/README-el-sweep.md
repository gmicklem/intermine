# ElSweep: find JSP EL expressions that current Tomcat rejects

Tomcat 8.5.100+ and 9.0.x reject Java keywords as EL property names (`widget.class`, `dec.boolean`, `${short ...}`):
"The identifier [class] is not a valid Java identifier as required by section 1.19 of the EL specification".
`ElSweep.java` finds every such expression in a webapp source tree without deploying anything. It extracts each
`${...}` and `#{...}` from `.jsp`, `.jspf`, `.jspx`, `.tag` and `.tagx` files and parses it with Tomcat's own
`jasper-el` (`ExpressionFactoryImpl.createValueExpression`). `/build/` and `/.git/` paths are skipped.

## Run

Needs a JDK (8 or later) and two jars from the Tomcat you care about (its `lib/` directory): `el-api.jar` and `jasper-el.jar`.
From a distribution (`https://archive.apache.org/dist/tomcat/`, the `apache-tomcat-X.Y.Z.tar.gz` file) or from a container:

```bash
mkdir -p /tmp/elsweep/jars /tmp/elsweep/out
for j in el-api jasper-el; do cp /path/to/apache-tomcat-X.Y.Z/lib/$j.jar /tmp/elsweep/jars/; done   # from a container: docker cp <container>:/usr/local/tomcat/lib/$j.jar /tmp/elsweep/jars/
CP=/tmp/elsweep/jars/el-api.jar:/tmp/elsweep/jars/jasper-el.jar
javac -cp $CP -d /tmp/elsweep/out ElSweep.java
java -cp /tmp/elsweep/out:$CP ElSweep /path/to/mine/webapp/src /path/to/intermine/intermine/webapp/src
```

Pass one or more source directories. Run it from a directory that holds no untrusted `.class` files.
Output is one `BAD file:line expression -> message` line per failing expression, then
`files=N expressions=M failures=K`. The exit status is 1 if there is any failure. Use the jars of the Tomcat version you deploy on,
because the identifier check differs between versions (older 8.5.x releases accept keywords).

## Stub function mapper and the false-positive filter

Taglib functions (`fn:`, `imf:`) are not loaded, so a stub `FunctionMapper` resolves every prefixed function to a one-argument method.
Jasper checks argument counts against that method, so any call with another number of arguments reports
`Function [x] specifies [1] params, but [n] were declared`.
Those messages are the stub's doing, not the JSP's, and `ElSweep` drops them. Parse-time errors, which include the identifier check,
are raised before the arity check, so this filter cannot hide a keyword failure. (About 170 such false positives appear without it on InterMine 5.1.1.)

## Results

Run on the webapp sources of an InterMine tree (`intermine/webapp/src` and `bio/webapp/src`, or a mine's `webapp/src`), the sweep
before the fixes reported 10 failures, all of them `.class` or other Java-keyword property names in `widget.jsp`, `template.jsp`,
`historyBagView.jsp`, `objectView.jsp`, `tablePageLinks.jsp` and `bio/webapp/.../model/dataSourceShort.jsp` (the .class and keyword
fixes of im-jqz.37 / im-jqz.38, applied by im-89g.17). With those fixes it reports **0 failures**.

The `files=N expressions=M` counts depend on the branch: the 5.1.1 release line and `maint-2026` carry different numbers of JSP files
and expressions, so compare the failure count, not the totals. Line numbers of the failures above are those of the unfixed 5.1.1
candidate and are only a guide.

Fix pattern: use `imf:className(obj)` / `imf:simpleClassName(obj)` (`WebUtil`, `WEB-INF/functions.tld`) instead of `.class.name`,
and bracket notation (`dec['boolean']`, `pageScope['short']`) for other keywords.
