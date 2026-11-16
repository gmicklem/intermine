# Webapp sweeps: dead message keys, dangling class references, broken tiles

Three Python 3 scripts (no dependencies) that find leftovers of removed features in the legacy webapp. Run them
from the repository root before a release; each reads files only and prints what it found.

| Script | Question it answers | Clean result |
|---|---|---|
| `msgkeys.py` | Which keys of `InterMineWebApp.properties` are used nowhere (JSP, tag, Java, JS, XML, other properties files, the mines)? | `unused 0` |
| `tilescheck.py` | Does every tile definition have its JSP, its `controllerUrl` action and a user, and does every struts action class exist? | no output below the two headings |
| `classrefs.py` | Does every `org.intermine.*` class name written in a JSP, tag, TLD, XML or properties file have a source file? | only references that name a class nobody provides (see below) |

```bash
W=intermine/webapp/src/main/webapp
M="$HOME/mines/humanmine $HOME/mines/flymine"      # checkouts of mines whose JSPs and webconfig use the standard tiles (optional)
python3 -I docs/tools/msgkeys.py $W/WEB-INF/classes/InterMineWebApp.properties . $M | head -3
python3 -I docs/tools/tilescheck.py $W .
python3 -I docs/tools/classrefs.py . $M -- . $M
```

* `msgkeys.py <properties-file> <root>...`: a key is used if its full name occurs in any text file under the
  roots, if it starts with a computed prefix (`"errors." + x`, `key="foo.${x}"`), or if it is a `<pageName>.title`,
  `.tab` or `.noFollow` key (`layout.jsp` and `headMenu.jsp` read these for every `pageName` set by a tiles definition,
  in `tiles-defs.xml` or in a mine's `tiles-defs-model.xml`). Anything under `UNUSED` can be deleted after a look at
  `${...}` lookups such as `<fmt:message key="${messageKey}">`.
* `tilescheck.py <webapp-src-main-webapp> <java-root>...`: parses `WEB-INF/tiles-defs.xml` and `WEB-INF/struts-config.xml`
  (comments are ignored).
* `classrefs.py <scan-root>... -- <java-root>...`: classes generated from the model XML (`org.intermine.model.bio.*`,
  `org.intermine.model.testmodel.*`) and property keys written below a package (`org.intermine.webservice.server.jbrowse.genomic.paths.end`)
  are not reported. What is left is a real dangling reference (a mine's `webconfig-model.xml` or `struts-config-model.xml` naming
  a class that was removed or moved, a properties file naming a removed class): fix or delete it.
