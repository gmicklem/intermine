# Upgrading a mine's build (deployer guide)

This guide is for people who run a mine (humanmine, flymine and similar) whose `build.gradle` uses the InterMine Gradle plugin (`classpath group: 'org.intermine', name: 'plugin', version: ...`). It covers two releases, in two separate parts:

- **5.1.1, non-breaking** (Gradle 6.9.4, JDK 8; branch `release/5.1.1-candidate` of https://github.com/gmicklem/intermine): a mine can stay on Gradle 4.9, or move to 6.9.4. See [5.0.x or 5.1.0 to 5.1.1](#50x-or-510-to-511).
- **6.0.0, breaking** (Gradle 8.14.6, JDK 17; branch `maint-2026`): the mine's build files must change. See [5.1.1 to 6.0.0](#511-to-600-gradle-814) and the notes around it.

Neither has been released yet. The tree of `maint-2026` is versioned `6.0.0` (since im-89g.10; earlier it carried `5.1.1`, which was not the 5.1.1 release), so artifacts built from it are called 6.0.0 and a deployer testing against an unreleased checkout uses `6.0.+` (or `6.0.0`) after publishing it to the local Maven repository.

The sources are the maintainers' planning notes (`decisions/gradle-upgrade-path.md`, `baselines/intermine-gradle-6.9.4-deprecations.md` and `plans/10-intermine-backend.md` in https://github.com/gmicklem/intermine-planning) a smoke build of a copy of flymine on Gradle 6.9.4 with the 5.1.1 plugin, and the conversion of HumanMine (`afdd8e9`) and FlyMine (`a507ed84`) to Gradle 8.14.6 with the plugin of `maint-2026` (now the `maint-2026` branches of https://github.com/gmicklem/humanmine, commits `f426ac8` and `58fbf8f`, and https://github.com/gmicklem/flymine, commits `3904f70b` and `e7ed6215`). Line numbers in the 5.1.1 section are those of the flymine copy (humanmine is the same, a few lines apart).

## Which versions go together

| InterMine plugin | Gradle in the mine | JDK | Status |
|---|---|---|---|
| 5.0.x, 5.1.0 | 4.9 | 8 | the current mines |
| 5.1.1 | 4.9 (checked) or 6.9.4 (checked); **6.9.4 is recommended** | 8 (Gradle 6.9.4 does not run on JDK 17) | branch `release/5.1.1-candidate`; not yet released |
| 6.0.0 | 8.14.6 | build: 17 (8 still works, 21 checked); run: 8, 17 or 21, see [JDK](#jdk-for-600) | branch `maint-2026` (version number `6.0.0`); not yet released |

Plugin 6.0.0 needs Gradle 8.14 in the mine: the plugin is compiled against Gradle's own Groovy, and a plugin built for a newer Groovy fails on an older Gradle. Plugin 5.1.1 is built so that it still runs on Gradle 4.9. A mine that pins `imVersion=5.1.+` (flymine does) picks up any 5.1.x plugin automatically, and cannot pick up 6.0.0 by accident; a mine that pins `5.0.+` stays on 5.0.x.

## Three edits that almost every mine needs

HumanMine and FlyMine needed these three changes in their own build files, on either line. Each is explained in the step lists below.

1. **Delete `inputs.sourceFiles.stopExecutionIfEmpty()`** from the `copyMineProperties` task in the root `build.gradle` (flymine line 50, humanmine line 53). Gradle 5.0 removed the method, so a mine does not configure on Gradle 5, 6.9.4, 7 or 8 until the line is gone; only Gradle 4.9 still has it. An empty `Copy` is skipped by Gradle itself, so nothing replaces it.
2. **Gretty.** `webapp/build.gradle` applies gretty from a script on raw.githubusercontent.com, and that script fetches gretty 2.0.0 from jcenter, which is dead. The mine does not configure on any Gradle. Remove the `apply from` line and the `gretty { }` block; the `cargo` plugin deploys. On Gradle 4.9 only, you can instead copy the script and point it at `mavenCentral()` (5.1.1 step 4).
3. **The Sequence Ontology paths** `soTermListFilePath` and `soAdditionFilePath` in `dbModelConfig { }` of `dbmodel/build.gradle`. Plugins up to and including the 5.1.1 release candidate hand these strings to Ant unchanged, and Ant resolves a relative path against the working directory of the Gradle daemon's JVM. That is the mine's root only by luck of how the daemon was started: on a Gradle 8 daemon on JDK 9 or later it is the daemon's own directory (`Could not find file containing SO terms ...`). Write them as absolute paths: `"$rootDir/dbmodel/resources/so_terms"` and `"$rootDir/dbmodel/build/so_additions.xml"` (or `rootProject.file('dbmodel/resources/so_terms').absolutePath`; `file(...)` and `project.file(...)` inside `dbmodel/build.gradle` are relative to `dbmodel/`, not to the root). The 6.0.0 plugin resolves relative paths against the mine's root itself, so both forms work there.

## Two deployer warnings for the webapp (both lines)

These are about what runs in Tomcat, not about the build. They apply to the 5.1.1 and the 6.0.0 lines alike, and to any mine that has its own JSPs, displayers or `web.properties` entries.

### Tomcat rejects Java keywords as EL property names

Tomcat 8.5.100 and later, and 9.0.x, refuse an EL expression that uses a Java keyword as a property name, such as `widget.class`, `dec.boolean` or `${short ...}`. Older 8.5.x releases accepted them. The message in `catalina.out` is a `JasperException` that ends:

```
The identifier [class] is not a valid Java identifier as required by section 1.19 of the EL specification
```

**How it looks.** The server starts and most pages work. A page that contains the bad expression either fails with a 500, or returns 200 with the failing tile left blank. In InterMine itself the list widgets, the template pages and the My Data lists were affected (`widget.jsp`, `template.jsp`, `historyBagView.jsp` and `dataSourceShort.jsp` in bio-webapp; on 5.1.1 also `objectView.jsp` and `tablePageLinks.jsp`, which 6.0.0 removed). The pages are compiled when they are first requested, so a deploy that "works" can still hide the problem until someone opens the page.

**InterMine's own JSPs are fixed**: commits `710f6d0059`, `d1d758565e` and `c949317eac` on `maint-2026` (6.0.0), and `9f2076d604`, `08d84fdd0f` and `9fe4cc40b2` on `release/5.1.1-candidate` (5.1.1). A mine that takes the fixed InterMine webapp is clean for the pages that come from InterMine. **A mine's own JSPs can still have the same patterns**: the files in `webapp/src/main/webapp/model/*.jsp`, custom displayers, and any JSP or tag that overrides one of InterMine's.

**The fix patterns:**

| Before | After |
|---|---|
| `${dec.boolean}` | `${dec['boolean']}` (bracket notation, for any keyword: `dec['short']`, `pageScope['short']`) |
| `${object.class.name}` | `${imf:className(object)}` |
| `${object.class.simpleName}` or a type name taken from `.class` | `${imf:simpleClassName(object)}` |
| `${short}` or another keyword used as a variable name (`<c:set var="short" ...>`) | rename the variable (`shortName`) everywhere it is used |

`imf:className` and `imf:simpleClassName` are in `WEB-INF/functions.tld` of the InterMine webapp (functions of `WebUtil`). A JSP needs the usual directive, which most InterMine JSPs already have:

```
<%@ taglib uri="/WEB-INF/functions.tld" prefix="imf" %>
```

**Find them before your users do: `ElSweep`.** `ElSweep.java` parses every `${...}` and `#{...}` expression in the `.jsp`, `.jspf`, `.jspx`, `.tag` and `.tagx` files of a source tree with Tomcat's own EL parser (`jasper-el`), without deploying anything. It is part of this repository, in [`docs/tools/`](tools/): [`ElSweep.java`](tools/ElSweep.java) and its [README](tools/README-el-sweep.md). It needs a JDK (8 or later) and two jars from the `lib/` directory of the Tomcat you deploy on, `el-api.jar` and `jasper-el.jar`; use the jars of that Tomcat version, because the check differs between versions. Run it from a directory that holds no untrusted `.class` files (the commands below take `docs/tools/ElSweep.java` from an InterMine checkout, `$IM`):

```bash
IM=/path/to/intermine                              # a checkout of this repository
TOMCAT_LIB=/path/to/apache-tomcat-9.0.x/lib        # or copy the two jars out of the container image
CP=$TOMCAT_LIB/el-api.jar:$TOMCAT_LIB/jasper-el.jar
mkdir -p /tmp/elsweep/out
javac -cp $CP -d /tmp/elsweep/out $IM/docs/tools/ElSweep.java
java -cp /tmp/elsweep/out:$CP ElSweep /path/to/mine/webapp/src/main/webapp
```

Pass one or more directories (for example also a checkout of `intermine/webapp/src` to see InterMine's own). Each failure is one line, `BAD file:line expression -> message`, followed by `files=N expressions=M failures=K`; the exit status is 1 if there is any failure. It takes seconds: on InterMine 5.1.1 it reads about 246 files and 3786 expressions, found 10 failures before the fixes, and finds none after. `fn:` and `imf:` functions are not loaded, so the tool answers them with a stub and drops the resulting argument-count messages; that cannot hide a keyword failure.

**Stop-gap.** Tomcat can be told not to check identifiers, by a system property in `CATALINA_OPTS` (or `JAVA_OPTS`):

```
-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true
```

This only hides the problem: it switches off a check in the container that every later Tomcat configuration, and every other EL implementation, may apply again. Use it to get a site back while you fix the JSPs, then remove it.

### cdn.intermine.org is gone

`https://cdn.intermine.org` no longer answers. Up to 5.1.0 the InterMine webapp took its JavaScript and CSS from it in three places, so a mine built from an InterMine without the fix loses its scripts and styles (`jQuery is not defined` in the browser, and the list upload confirmation page waits for ever on "Verifying identifiers"):

- `head.cdn.location = https://cdn.intermine.org` in `WEB-INF/global.web.properties`, and every `head.js.*` / `head.css.*` property whose value starts with `CDN/` (`CDN` was replaced by `head.cdn.location` in `WebUtil.getHeadResources`): imjs, jQuery, underscore, Backbone, Q, the API loader, im-tables, the list widgets, pomme.js, font-awesome, bootstrap for the list pages, and the component-400 bundle that the bag upload confirm page loads;
- `htmlHead.jsp` and `layout.jsp`, which passed the same location on to the JavaScript libraries as `CDN.server`;
- the JavaScript code generator for queries and templates (`WebserviceJavaScriptCodeGenerator`), whose generated `<script src>` defaulted to `http://cdn.intermine.org`.

**What the fix does** (issues im-jqz.43 and im-jqz.45; it is in `maint-2026` and in the 5.1.1 candidate). The webapp serves its own copies, byte for byte from the public `intermine/CDN` repository, so a mine needs no external CDN for its own pages and works on an intranet:

- The files are in `<webapp>/vendor/` (`intermine/webapp/src/main/webapp/vendor/`, same layout as the old CDN). `vendor/README.md` lists every library with its version, path and the pages that use it; `vendor/SHA256SUMS` has the SHA-256 of each file (`sha256sum -c SHA256SUMS` from that directory checks the copy).
- A `head.js.*` / `head.css.*` value that starts with `CDN/` is served from `<webapp>/vendor/...`: `head.js.all.Ae = CDN/js/intermine/imjs/3.18.1/im.js` becomes `<webapp>/vendor/js/intermine/imjs/3.18.1/im.js` (`WebUtil.resolveHeadResourceUrl`). The `global.web.properties` of InterMine keep their `CDN/` values.
- `head.cdn.location` is removed. A mine that still sets it in its `web.properties` gets one warning in the log (`The web property head.cdn.location (...) is ignored: the libraries are served by the webapp itself from /vendor/`) and the value is not used.
- An absolute `http(s)://cdn.intermine.org/...` URL in a mine's own `head.*` property is mapped to the bundled copy of the same path, with a warning (`Head resource ... names the defunct cdn.intermine.org, using the copy bundled in the webapp instead`). It works, but only for files that `vendor/` has; change the value to the `CDN/` form.
- `htmlHead.jsp` and `layout.jsp` point the JavaScript libraries (`CDN.server`, the API loader's root) at `<context>/vendor`, and the code snippet generator now emits the mine's own im-tables 2.2.0-beta (`<mine>/vendor/js/intermine/im-tables/2.2.0-beta/imtables.js` and `main.sandboxed.css`, with the 2.x `imtables.loadTable` API) instead of a script from a third-party host. It also links the mine's `css/im-tables-fonts.css`, and `/vendor/fonts/*` is served with CORS headers, so a snippet pasted into another site contacts no host but the mine.
- `branding.images.logo` defaults to `images/intermine-logo-45x45.png`, a file of the webapp (rawgit, where the old default lived, has been shut down). The `/service/branding` web service is read by clients on other sites, so it turns every `branding.images.*` value without a scheme into a full URL under the mine's base URL (`images/logo.png` becomes `<base URL of the webapp>/images/logo.png`); an absolute or protocol-relative value is returned as it is.
- **5.1.1:** the only third-party script left is the Google Charts loader, `https://www.gstatic.com/charts/loader.js` (`head.js.bagDetails.Ca`), and only on the list analysis page (`bagDetails.do`): the vendored list-widgets 2.1.4 draws its charts with `google.visualization` and does not start without it. No other page contacts Google. A mine that must not contact Google has to replace the list widgets app: without the loader the list analysis page stops with `google is not defined`. **6.0.0:** not even that one: `head.js.bagDetails.Ca` is `google-charts-shim.js`, a file of the webapp (`js/google-charts-shim.js`) that provides the few calls the list widgets make (`google.load("visualization", ...)`, the column, bar and pie charts and their selection events) and draws inline SVG, so no page contacts Google (a mine that sets `head.js.bagDetails.Ca` to the gstatic loader gets the Google charts back; any other Google chart type is not provided). On both lines Google Analytics only loads when `google.analytics.id` is set and the visitor agreed to tracking.
- Two things stay as shipped inside the vendored files: a json3 fallback for Internet Explorer 7 and older in imjs and im-tables that names `cdn.intermine.org` and never runs in a supported browser, and the im-tables stylesheet's request to `netdna.bootstrapcdn.com` for icon fonts, which `css/im-tables-fonts.css` overrides with the fonts in `vendor/fonts`. See `vendor/README.md`, "Known remaining external references".

**What a deployer checks**, in the mine's own source tree:

```bash
grep -rn cdn.intermine.org webapp/                  # JSPs, JS, CSS, properties
grep -rn "head.cdn.location\|CDN/" webapp/src/main/webapp/WEB-INF/web.properties
```

- A mine that does not touch `head.*` needs nothing.
- A mine that overrides `head.js.*` or `head.css.*` and keeps a `CDN/` prefix gets the bundled copy, so the entry works if the file is in `vendor/`. Check the path against `vendor/README.md`: a `CDN/` path that the old CDN had and `vendor/` has not is a 404.
- A mine that **hosts its own CDN mirror** (it sets `head.cdn.location` to its own server) must change the entries: `head.cdn.location` is ignored now, so `CDN/` no longer reaches the mirror. Write those entries as absolute URLs of the mirror (`https://static.example.org/js/...`), which are used as they are, or as `CDN/` to take the bundled copy.
- A custom JSP, displayer or JavaScript file of the mine that loads from `cdn.intermine.org` must be edited by hand; the mapping above only covers `head.*` properties. Replace the reference with a path under the mine's own `js/` and `css/`, with a `vendor/` file (`<c:url value="/vendor/js/..."/>`), or with a pinned cdnjs URL (`https://cdnjs.cloudflare.com/ajax/libs/<library>/<exact version>/<file>`, with an `integrity=` hash if it is written into a JSP).

## 5.0.x or 5.1.0 to 5.1.1

This is the non-breaking release. Branch `release/5.1.1-candidate` (the last Gradle 6.9.4 state; its plugin is compiled with Gradle 6.9.4) was checked in a mine on **Gradle 4.9**: the plugin loads, the build configures, and the tasks `tasks`, `createSoModel` and (in `webapp`) `mergeProperties` start. It was also checked on **Gradle 6.9.4**, with the changes below. Loading data, `buildDB` of a full mine and deploying to Tomcat were not run for this line, and this branch needs JDK 8.

So nothing is required except `imVersion` and `bioVersion`, with one exception that does not depend on InterMine: a `webapp/build.gradle` that still has the `gretty` line (step 4) does not configure on any Gradle today, 4.9 included, because jcenter is gone. A mine that builds its war on this line also needs step 6 (`webXml`) and, on a fresh checkout, step 7 (run `copyWebappContent` once first). To move the mine to Gradle 6.9.4, which shows every deprecation that 8.14 will turn into an error:

1. Set `imVersion` and `bioVersion` in the mine's `gradle.properties` to `5.1.1`.
2. Update the wrapper: `./gradlew wrapper --gradle-version 6.9.4 --distribution-type bin`.
3. Delete the line `inputs.sourceFiles.stopExecutionIfEmpty()` (flymine `build.gradle:50`, humanmine `build.gradle:53`). **Gradle 5.0 removed that method, so without this edit a mine does not configure on any Gradle from 5.0 on.** It is the only edit that is needed to configure on 6.9.4: an empty `Copy` task is skipped as `NO-SOURCE` by Gradle itself.
4. Deal with the `gretty` line (flymine `webapp/build.gradle:2`): an `apply from: https://raw.githubusercontent.com/akhikhl/gretty/...` script. Its buildscript resolves `org.akhikhl.gretty:gretty:2.0.0` from jcenter, which is dead (it returned 403 in the smoke build), so the build cannot configure. This is not a Gradle-version problem, and it hits a mine on Gradle 4.9 as well. Two ways out:
   - **Remove gretty** (what InterMine recommends): delete the `apply from` line and the `gretty { }` block of the same file. The `cargo` plugin does the deployment. Gretty was already dropped from testmine in InterMine's own `RELEASE_NOTES` because it does not work with Gradle 4.9, and the script is an unpinned remote file.
   - **Keep gretty**: gretty 2.0.0 itself is on Maven Central, only the script looks for it in jcenter. Save the script in the mine's repository (from a fixed commit of akhikhl/gretty, not `master`), change the `jcenter()` in its `buildscript { repositories { } }` to `mavenCentral()`, and point the `apply from:` at that copy. This was not tried here. Gretty 2.0.0 dates from the Gradle 4 era and is not known to work on Gradle 6.9.4 or 8.14, so it is a stop-gap for a mine that stays on Gradle 4.9; the 6.0.0 steps below remove it.
5. In `dbmodel/build.gradle`, make `soTermListFilePath` and `soAdditionFilePath` absolute (`"$rootDir/..."`, see "Three edits" above). The 5.1.1 plugin does not resolve them. It is not needed while the daemon happens to start in the mine's root, but it is cheap and it carries over to 6.0.0.
6. **Set `webXml` on the mine's war task** (a mine of this line composes its war in a `warWebApp` task of `webapp/build.gradle`; flymine `webapp/build.gradle:38-44`). Gradle's `War` task takes `WEB-INF/web.xml` through its own `webXml` property, not through the generic `from(...)` copy, and the mine's `warWebApp` never set it. In the testbed's build of the 5.1.1 line (Gradle 6.9.4) the war that came out had no `web.xml` at all: it deployed, Tomcat logged success, and every request returned 404 because no servlet mapping existed. The real file, with the struts and tiles additions of the mine's model merged in by `addStrutsConfig`, is `build/explodedWebApp/WEB-INF/web.xml`. Point `webXml` at it, **and** exclude it from the `from`, or the war gets the entry twice (two overlapping `web.xml` entries, which `unzip` refuses as a possible zip bomb):

   ```groovy
   task warWebApp(type: War) {
       dependsOn 'copyWebappContent'
       description "Create the war from the build/explodedWebAppDir directory"
       from "${explodedWebAppDir}"
       exclude "WEB-INF/web.properties", "WEB-INF/web.xml"                 // was: exclude "WEB-INF/web.properties"
       webXml = file("${explodedWebAppDir}/WEB-INF/web.xml")               // new
       webInf { from "${mergePropsDir}" }
   }
   ```

   This is on the 5.1.1 line only: the 6.0.0 plugin composes the war itself and sets `web.xml` (see "5.1.1 to 6.0.0" below), so the line goes away with the rest of `warWebApp`. It was not checked on Gradle 4.9.
7. **The first `:webapp:war` of a fresh checkout fails** with `File .../build/explodedWebApp/WEB-INF/web.xml specified for property 'webXml' does not exist` (the `webXml` of step 6 is what makes it visible; without it the first war is built, with most of the bio-webapp content missing). The cause is the known first-run wipe of Gradle: `unwarBioWebApp` unpacks the `bio-webapp` war into `build/explodedWebApp`, and `copyWebappContent`, a `Copy` into the same directory, has no execution history yet. Gradle treats a task without history as the owner of nothing that exists in its output directory and removes it, so the copy deletes what the unwar had just written (2739 files down to 5, `web.xml` among them). Run the copy once on its own, first, so that it has a history, and then the war:

   ```bash
   ./gradlew :webapp:copyWebappContent
   ./gradlew :webapp:war
   ```

   Every later run has the history and passes, which is why a build that was run by hand before never shows it; a script that builds the mine from a fresh checkout (CI) must run the first command once. With the history in place the war's own `unwarBioWebApp` and `copyWebappContent` sequence leaves both sets of files, the unpacked war overlaid with `src/main/webapp`. The 6.0 plugin composes the war from the outputs of the tasks that make each part (`unwarBioWebApp` is a Sync there, and `copyWebappContent` is gone), so a first run is no different from a later one and this note does not apply.
8. Run `./gradlew --warning-mode all :dbmodel:tasks` to see the remaining deprecations. In the flymine smoke build on 6.9.4 they were:

| Warning | Where (flymine copy) | Fix |
|---|---|---|
| `maven` plugin | `build.gradle:22` | delete `apply plugin: 'maven'` |
| `jcenter()` repository (removed in Gradle 8) | `build.gradle:30` | delete it |
| `compile` / `testCompile` | `build.gradle:38,40`, and `dbmodel/` and `webapp/` build files | `implementation` / `testImplementation` (see below) |

## JDK for 6.0.0

InterMine 6.0.0 is built and tested on JDK 17, and its jars are still Java 8 bytecode (class file version 52), so a mine can keep a JDK 8 Tomcat. To move a mine to JDK 17 (or 21) (the converted HumanMine builds its war on JDK 17 and on JDK 8):

- **Build JDK.** The JDK that runs Gradle (`JAVA_HOME`) is the one that compiles the mine, and the plugin's tasks (`integrate`, `buildDB`, `postprocess`, ...) run inside the Gradle daemon on it. Set `JAVA_HOME` before `./gradlew`; no toolchain setting is needed. Gradle 8.14 runs on JDK 8 to 24.
- **If Tomcat stays on JDK 8 but the mine is built on 17**, add `tasks.withType(JavaCompile).configureEach { options.release = 8 }` to the mine's Java projects, so that its own code is compiled against the Java 8 API (`sourceCompatibility = 1.8` alone does not do that).
- **No JVM option on JDK 16+.** Up to 5.1.1 InterMine used cglib 3.1 to build objects that belong to more than one model class, and on JDK 16+ cglib needed `--add-opens=java.base/java.lang=ALL-UNNAMED` (in Cargo's `jvmArgs`, a mine's `test` tasks, a Tomcat not started by `catalina.sh`). 6.0.0 uses Byte Buddy instead (see [cglib is gone](#cglib-is-gone-600)), which needs no option; a mine that added `--add-opens=java.base/java.lang=ALL-UNNAMED` for InterMine can delete it.
- **psi-complexes: small molecules get their ChEBI name from the ChEBI REST API (6.0.0).** Up to 5.1.1 the converter looked the name up at the ChEBI SOAP web service with a vendored JAX-WS client, which JDK 11 cannot run and which the service stopped answering (HTTP 500, Nov 2026), so no small molecule had a name on JDK 11+ and, since then, on JDK 8 either. 6.0.0 asks `https://www.ebi.ac.uk/chebi/backend/api/public/compound/CHEBI:<id>/` (plain `HttpURLConnection`, 5 s to connect, 10 s to read, `ascii_name` of the answer as before; each compound once per load) and keeps the answers for the run. A ChEBI that cannot be reached never stops the load: the molecule is stored without a `name` and the failure is logged, and after three failed requests in a row the converter stops asking for the rest of the run. The load machine therefore needs HTTPS access to `www.ebi.ac.uk` (through the JVM's `https.proxyHost` settings if there is a proxy); to load without it, give the source `<property name="chebi.url" value=""/>` (an empty value switches the lookup off), or point `chebi.url` at a mirror of the compound endpoint (the URL up to and including the final `/`). The converter now needs `jackson-databind`, which is in its POM. Two more changes in the same converter: an `InteractionDetail`'s `allInteractors` lists all the interactors of the complex (it listed only the ones read before the detail's participant), and an undetermined range (`?-?`) no longer gives a `Location` 0..0 and an `InteractionRegion`.

## 5.1.1 to 6.0.0 (Gradle 8.14)

This is the list a deployer follows, in order. It was run on HumanMine (`imVersion` 5.0.+, Gradle 4.9) and FlyMine (5.1.+, Gradle 4.9) at their upstream master, with the plugin and the InterMine artifacts built from `maint-2026` (which carried the number 5.1.1 then and is `6.0.0` now; "the 6.0 line" means `maint-2026`), and run again with the final plugin (webapp plugin composing the war, relative SO term paths, commons-lang3, Checkstyle 9.3, JDK 17 support). Both mines then pass `:dbmodel:buildDB` and `:webapp:war` on Gradle 8.14.6 with JDK 17 (HumanMine also with JDK 8), with no deprecation from their own build files, and the war has no repeated library. Loading data (`integrate`, `postProcess`) and deploying to Tomcat were not run.

You can do steps 1 to 7 directly on a mine that is still on Gradle 4.9; the edits do not depend on the 5.1.1 section above.

### Before you start

- **JDK 17** for Gradle (`export JAVA_HOME=...`). JDK 8 also works, with Gradle's deprecation notice; see [JDK](#jdk-for-600).
- **The mine's property file**, `~/.intermine/<mine>.properties` (`humanmine.properties`, `flymine.properties`; the name is `mineName` in the root `build.gradle`, plus `.<release>` when you pass `-Prelease=...`). `System.env.HOME` is read, so run the build with the `HOME` that has the file. It is not in the repository. The keys the build reads are `db.production.datasource.{serverName,databaseName,user,password}`, the same four for `db.userprofile-production` (and `db.common-tgt-items` for `buildTgtItems`), `webapp.path`, `webapp.deploy.url`, `webapp.manager` and `webapp.password` (cargo), `superuser.account`, `superuser.initialPassword`, `project.title` and the other `project.*` keys of your mine. `config/testmodel.properties` in this repository is the shortest example (it uses the `unittest` aliases of testmine; a production mine uses `db.production` and `db.userprofile-production`).
- **The mine's own data source jars on Maven.** The webapp compile needs the full data-source set, not only the sources that InterMine publishes: see the next section. Do this first, or the build stops at step 7.
- **Unreleased 6.0 artifacts** (until 6.0.0 is on Maven Central): in this checkout run `./gradlew publishToMavenLocal` in `plugin`, `intermine`, `bio`, `bio/sources` and `bio/postprocess`, in that order, after `config/lib/install_intermine.py` (see [BUILDING.md](BUILDING.md)). The mine's `buildscript` and `repositories` already list `mavenLocal()` first. If you publish to another directory, pass `-Dmaven.repo.local=<dir>` to the mine's build too.

### The data sources that are not on Maven Central

The plugin turns every `<source>` of the mine's `project.xml` into a dependency `org.intermine:bio-source-<type>:<version>` (the `name` for `intermine-items-xml-file` sources). Generating the model merges the `*_additions.xml` of all those jars into `genomic_model.xml`, and the generated classes (`org.intermine.model.bio.*`) are what the mine's `webapp` Java code compiles against. So:

- A source whose jar is missing stops the build in `:dbmodel:generateKeys` with `Could not find org.intermine:bio-source-redfly:9.9.9` (the message lists every repository it tried).
- A `project.xml` that is cut to the sources that you have gives a model without the classes of the others, and `:webapp:compileJava` fails with `error: cannot find symbol  import org.intermine.model.bio.MicroArrayAssay;` (FlyMine, cut to the 47 sources of InterMine; the same for `FlyAtlasResult`, `MicroArrayResult`). It is data, not Gradle: restore the sources. Note that `mergeModels` and `generateModel` only run while `dbmodel/build/gen` (the generated Java classes) does not exist, whatever `project.xml` says, so after editing `project.xml` delete `dbmodel/build` (or run `./gradlew :dbmodel:clean`).

This repository's `bio/sources` build publishes 44 `bio-source-*` modules. The others that the two mines list are in the mines' own repositories and are not on Maven Central: HumanMine's 16 (`ncbi-summaries`, `mgi-alleles`, `arrayexpress-atlas`, `protein-atlas`, `protein-atlas-rna`, `atlas-express`, `clinvar`, `huge-gwas`, `hpo`, `hpo-annotation`, `orphanet`, `disgenet-disease-associations`, `depmap-sample-info`, `depmap-expression`, `gtex`, `humanmine-static`, version 4.3.0) are in https://github.com/intermine/humanmine-bio-sources, FlyMine's 19 (`drosophila-homology`, `rnai`, `flyatlas`, `flybase-alleles`, `redfly`, `fly-fish`, `miranda`, `affy-probes`, `flymine-static`, ..., version 4.2.0) in https://github.com/intermine/flymine-bio-sources. Those two repositories are Gradle 4.9 builds too (`compile`, `maven`, `signing`, `uploadArchives`; the `*-static` sources also apply the bintray and artifactory plugins, which no longer resolve) and publish nothing to Central. To build a mine you therefore convert and publish them first, to your own Maven repository or to `mavenLocal()`: `java-library` and `maven-publish` instead of `java`, `maven`, `signing` and `uploadArchives`; `api` instead of `compile`; a `jar { exclude 'intermine.properties' }`; drop the bintray, artifactory and checkstyle parts; `publishToMavenLocal` at the version the mine's `project.xml` asks for (`version="4.3.0"` / `"4.2.0"`; a source without `version` takes `bioVersion`). The test run for this guide did that in a scratch copy of each repository (nothing was committed): a new root `build.gradle` of about 70 lines (the `buildscript` with the InterMine `plugin` from `mavenLocal()`; `java-library`, `maven-publish`, `dbmodel` and `biosource-dbmodel` on every subproject; the `java { }` block and `options.release = 8`; the `bioModel` and `mergeModel` configurations; `api` for `bio-core`, `intermine-resources` and `commons-collections:3.2`; `mergeModel` on `intermine-objectstore` and `bioModel` on `bio-model` with `transitive: false`; `dbModelConfig`, plus `bioSourceDBModelConfig { globalAdditionsFile = ... }` for FlyMine; the same `copyMineProperties` task and `processResources { from copyMineProperties }` as the mines; `jar { exclude 'intermine.properties' }`; a `publishing { }` block with `from components.java`), the two `*-static` build files cut down to their `sourceSets`, one `api bio-model` and the `processResources` line (their bintray, artifactory and signing parts removed), a `gradle.properties` with `systemProp.imVersion` and `systemProp.bioVersion` (`5.1.1`) and the Gradle 8.14.6 wrapper files of this repository. The checkstyle, sources-jar and javadoc-jar parts of the old root files were dropped. The converters need no `log4j` declaration of their own (`org.apache.log4j` arrives through `bio-core`). For `commons-lang` that was true of the first conversions, but 6.0.0 no longer puts commons-lang 2 on any classpath, so a converter that imports `org.apache.commons.lang` must move to `org.apache.commons.lang3` ([commons-lang 2 is gone](#commons-lang-2-is-gone-600); HumanMine's and FlyMine's source repositories did, with `import org.apache.commons.lang3.StringUtils`). `bio-core` is `api` for `commons-lang3`, so a project that takes `bio-core` with its POM has lang3 without a line of its own; one that takes it with `transitive: false` declares `commons-lang3` itself. Nothing else in those repositories needed to change, and all the source jars of both mines compiled on JDK 17 with `--release 8` (`./gradlew -Dmaven.repo.local=<dir> publishToMavenLocal -x test`, about 30 seconds each). Converting those two repositories properly is a separate piece of work.

### Steps

1. **Wrapper.** Gradle 8.14.6, committed with its jar as before:

   ```bash
   ./gradlew wrapper --gradle-version 8.14.6 --distribution-type bin \
       --gradle-distribution-sha256-sum 7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24
   ```

   On the old Gradle this works as long as the build still configures (it does for the 5.1.1 section); otherwise run `gradle wrapper ...` from an empty directory with any Gradle 8.14 and copy `gradlew`, `gradlew.bat` and `gradle/wrapper/*`. Behind a proxy that rejects the HEAD request, the task stops with `Test of distribution url ... failed`; add `--no-validate-url` and delete the `validateDistributionUrl=false` line that this writes into `gradle-wrapper.properties`.
2. **`gradle.properties`.** `systemProp.imVersion` and `systemProp.bioVersion` to `6.0.+` (while testing against the unreleased branch, the same `6.0.+` resolves the `6.0.0` that you published to your local Maven repository). `5.0.+` / `5.1.+` cannot pick up a 6.0 plugin by accident, and `6.0.+` stays on 6.0.x. `systemProp.javax.xml.stream.XMLOutputFactory` is no longer needed on 6.0.0 (the StAX writers of InterMine flush, so they give the same text with the JDK's implementation and with Woodstox, which solr-solrj brings in; im-ee7.67); it is harmless if kept, and still works on JDK 8 and 17. (The `javax.xml.stream.XMLOutputFactory` line of the webapp's `global.web.properties` is a different thing: `InitialiserPlugin` reads it at start-up, so leave it in place.)
3. **Root `build.gradle`**, in `buildscript` and the `subprojects` block:
   - add `gradlePluginPortal()` to the `buildscript` repositories and raise `com.bmuschko:gradle-cargo-plugin` to `2.9.0` (2.9.0 is only on the Gradle plugin portal, not on Maven Central: without the repository the build stops with `Could not find com.bmuschko:gradle-cargo-plugin:2.9.0`; 2.8.0 is on Central and configures and runs the war tasks on 8.14.6, but neither version was run against a Tomcat here);
   - delete `apply plugin: 'maven'`, `jcenter()` and `inputs.sourceFiles.stopExecutionIfEmpty()`;
   - `sourceCompatibility`/`targetCompatibility` into a `java { }` block, and add `options.release = 8` for `JavaCompile` when `JavaVersion.current().isJava9Compatible()` (a JDK 8 `javac` has no `--release`);
   - `compile` to `implementation`, `testCompile` to `testImplementation`; `url '...'` to `url = '...'`, `description "..."` to `description = "..."`;
   - delete `compile('ant:ant:1.6.5')`: it is a 2005 Ant that ends up in `WEB-INF/lib` next to `ant-1.10.x`, and nothing in the mine needs it. (`ant:ant` is the groupId of Ant before it moved to `org.apache.ant`; Gradle takes the two for different libraries, so no version conflict resolution joins them. Where it comes from: the mine's own line, and also the published POMs of the 5.x modules themselves, which declare `ant:ant:1.6.5` with scope `compile` (checked on Maven Central for `intermine-api`, `intermine-objectstore`, `bio-core` and `intermine-webapp`, versions 5.0.8 and 5.1.0), so a mine on 5.x gets the jar transitively even without the line. The 6.0 POMs carry only `org.apache.ant:ant` (im-89g.10; the 5.x build files had the legacy coordinate, the 6.0 catalog has the Apache one), so a mine that resolves `6.0.+` does not get it transitively. The plugin of 6.0 leaves the `ant:ant` jar out of the war whatever the mine declares and prints a warning when it does so (`The war leaves out ant-1.6.5.jar ...`), so a mine that keeps the line gets one Ant in its war, but the line is still dead weight on the mine's other classpaths: delete it);
   - `copyMineProperties` writes into a directory of its own and `processResources` picks it up (explained below the example).
4. **`dbmodel/build.gradle`.** `compile` to `implementation` (`bioModel` stays as it is); keep `srcDirs = ['build/gen']`. Leave the two paths in `dbModelConfig` (`soTermListFilePath = "dbmodel/resources/so_terms"`, `soAdditionFilePath = "dbmodel/build/so_additions.xml"`) as they are: the plugin resolves a relative path against the mine's root directory, absolute paths still work, and nothing needs `$rootDir` any more. (Plugins before this fix (the 5.1.1 builds of this branch included) handed the string to Ant, which resolves it against the working directory of the JVM, and a Gradle 8 daemon on JDK 9 or later has its own `daemon/8.14.6` directory as working directory: `Could not find file containing SO terms to add to the model ... Property was: <gradle user home>/daemon/8.14.6/dbmodel/resources/so_terms`. A mine that was converted with the `$rootDir/...` form keeps working.)
5. **`webapp/build.gradle`.**
   - Delete the `apply from: "https://raw.githubusercontent.com/akhikhl/gretty/..."` line and the `gretty { }` block. The script resolves gretty 2.0.0 from jcenter, which now answers 403. The cargo plugin deploys. Gretty also passed `-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true` to its Tomcat; your own Tomcat needs it too (`CATALINA_OPTS`, or `jvmArgs` in a cargo `local { }` block).
   - `compile` to `implementation`. Keep `implementation (project(":dbmodel")) {transitive = false}`; there is no need for `java-library` or `api`, because the webapp lists what it needs itself.
   - `JavaExec`: `main = ...` to `mainClass = ...` (`blueGenesStart`; Gradle 8.14 deprecates `main`, Gradle 9 removes it).
   - War composition: the plugin builds the war now, so delete everything in `webapp/build.gradle` that composed it: the `war { dependsOn ...; finalizedBy 'warWebApp' }` block, the `warWebApp` task, the `copyWebappContent` task, the `explodedWebAppDir` variable, the `tasks.named('mergeProperties') { inputs.files(...) }` block of the first conversions and any `tasks.withType(War) { classpath = classpath.filter { ... } }` block. `mergePropsDir` and `propsDir` in `webappConfig` can go as well: the plugin writes `web.properties` and `objectstoresummary.properties` to `build/props` unless `propsDir` says otherwise. What the plugin does instead: it applies the `war` plugin, and the `war` task takes, first the mine's own `src/main/webapp` (the war plugin adds it by itself), then `build/explodedWebApp`, which the `unwarBioWebApp` task fills (it is a Sync now: it unpacks the `bio-webapp` war and makes the struts, tiles, `web.xml` and message additions of the mine's model in the same task), then `WEB-INF/web.properties` (merged with `intermine.properties` by `mergeProperties`) and `objectstoresummary.properties` from `build/props`. Every part comes from the output of the task that makes it, so the war needs no `dependsOn`. `duplicatesStrategy` is `EXCLUDE` on the `war` task, so the first entry of a name wins: the mine's own files replace those of bio-webapp, and a jar (same file name, so same version) that the mine's classpath and bio-webapp both carry is listed once, so the war has no duplicate zip entries (`unzip -Z1 webapp.war | sort | uniq -d` prints nothing). Two versions of one library have two file names and are not entries of the same name: Gradle's conflict resolution keeps one version of a library within the mine's classpath, but cannot do so between that classpath and the jars inside the bio-webapp war. The plugin's `checkWarLibraries` task, which the `war` takes as an input, finds such a pair (the names of the libraries come from the resolved module ids of the mine's classpath, and from the file names for the jars inside the bio-webapp war) and warns `The war would carry two versions of a library in WEB-INF/lib`, naming both jars; `./gradlew -PimWarDuplicateLibraries=fail war` (or the same line in `gradle.properties`) stops the build instead, `ignore` silences the check. Keep one version: `configurations.all { resolutionStrategy.force 'group:name:version' }` to take the one of bio-webapp, or an exclusion on the dependency that brings the other. InterMine's own artifacts carry one version of each library (`org.apache.oltu.oauth2.client`/`common` 1.0.2, `org.apache.ant:ant` 1.10.x), and the plugin leaves the legacy `ant:ant` jar out of the war's classpath (`classpath` minus the artifacts of the `ant:ant` coordinate; the `runtimeClasspath` of the mine is not changed); the unmerged `WEB-INF/web.properties` of the mine and of bio-webapp are excluded. Your cargo tasks keep `dependsOn war`; the `addStrutsConfig` task still exists, empty, for build files that mention it. A mine that does not update still gets the same war: a `warWebApp` that it keeps is rebuilt over the plugin's war from the same files, and a `war { dependsOn ... }` is harmless. A `copyWebappContent` has to go: it is a Copy into the directory that `unwarBioWebApp` now owns.
   - Why this was a plugin job: `warWebApp` zipped `build/explodedWebApp`, which `unwarBioWebApp` and `addStrutsConfig` filled (the unpacked `bio-webapp` war; an Ant unzip that never removed anything, so a file of an earlier bio-webapp version stayed in the directory), and `copyWebappContent` then copied `src/main/webapp` into the same directory. On Gradle 8, the first run of a Copy task into a directory that other tasks wrote removes their files, with no message: the war was built (46 MB instead of 68 MB for FlyMine) without the bio-webapp content (no `web.xml`, `struts-config.xml` or `WEB-INF/lib` of bio-webapp; on the next run the directory had them again, so a build that is repeated hides it). (The POM of `intermine-webtasks` 5.1.0 on Maven Central, and every plugin build before the fix of im-ee7.46 (the 5.1.1 release candidate included), named the 12 MB `intermine-webapp` war as a dependency (`intermine-jbrowse-endpoint` the same in the latter), so the war of every mine carried `intermine-webapp-<version>.war` in `WEB-INF/lib` and the first conversions filtered it out of the war's classpath. The POMs of this branch name `intermine-webapp:classes` instead.)
6. **commons-lang in the mine's own Java** (`webapp/src`, `dbmodel/src` if there is any, and your `*-bio-sources` repositories). Nothing to do for InterMine's own classes: since 6.0 they import `org.apache.commons.lang3`, which every InterMine POM brings in (`commons-lang3` 3.21.0, in the war). **`commons-lang:commons-lang:2.6` is no longer on any classpath of a 6.0 mine: not on the compile classpath, not in `WEB-INF/lib`** (see [commons-lang 2 is gone](#commons-lang-2-is-gone-600)). Check your own code: `grep -rn 'org.apache.commons.lang\b' webapp/src dbmodel/src` (and in your bio-source repositories; JSPs and tag files can import it too, so grep `webapp/src/main/webapp` as well). HumanMine has no Java of its own that imports it in the mine, and FlyMine's six classes in `webapp/src/main/java/flymine/web` do not either; the 15 converters of `humanmine-bio-sources` and the 7 of `flymine-bio-sources` imported `org.apache.commons.lang.StringUtils` and moved to lang3. If something matches, either:
   - move it to `org.apache.commons.lang3` (nothing to declare in a war project: lang3 is on the compile classpath through the InterMine POMs; a bio-source project that takes `bio-core` with `transitive: false` declares `implementation 'org.apache.commons:commons-lang3:3.21.0'`). Renames and behaviour changes that matter: `StringUtils`, `ArrayUtils`, `ObjectUtils`, `SystemUtils`, `BooleanUtils`, `NumberUtils` (now in `org.apache.commons.lang3.math`) are mostly the same calls; `StringEscapeUtils.escapeXml` to `escapeXml10` (lang3 still has `escapeXml`, but the whole `StringEscapeUtils` class is deprecated in favour of commons-text; InterMine's own code uses `org.intermine.util.XmlUtil.escapeXml`), `escapeHtml` to `escapeHtml4` (or `escapeHtml3`), `unescapeHtml` to `unescapeHtml4`; `ObjectUtils.equals(a, b)` to `java.util.Objects.equals(a, b)` (the lang3 method is deprecated); `StringUtils.isNumeric("")` is `false` in lang3 and was `true` in 2.6; `Validate` differs in signatures and exceptions (`Validate.notNull(o)` and `notEmpty(...)` throw `NullPointerException` for `null`, where 2.6 threw `IllegalArgumentException`; the message arguments are `String, Object...` instead of `String, Object`, and `notNull` returns its argument); `WordUtils` is in `org.apache.commons.lang3.text.WordUtils` (deprecated since 3.6, still there in 3.21.0, so a plain package change works); its replacement is `org.apache.commons.text.WordUtils` in **commons-text**, which InterMine does not ship: a project that wants it declares `implementation 'org.apache.commons:commons-text:1.12.0'` (or a newer 1.x) itself, as for `StringEscapeUtils`'s successor `org.apache.commons.text.StringEscapeUtils`; `org.apache.commons.lang.enum.Enum` and `exception.Nestable*` have no lang3 equivalent (use a Java `enum` and ordinary exceptions); or
   - keep lang 2.6 and declare it yourself, `implementation 'commons-lang:commons-lang:2.6'` (and it goes in your war). InterMine neither ships nor provides it any more.
   Mixing both libraries in one mine is fine (`commons-lang-2.6.jar` and `commons-lang3-3.21.0.jar` have different packages and both stay in `WEB-INF/lib`).
7. **Check**, from a clean tree (`rm -rf build dbmodel/build webapp/build .gradle`):

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 HOME=<home with .intermine/<mine>.properties>
   ./gradlew --warning-mode all :dbmodel:tasks :webapp:tasks --dry-run     # configures, no deprecation
   ./gradlew --warning-mode all :dbmodel:buildDB                           # creates the production database tables
   ./gradlew --warning-mode all :webapp:war
   ```

   `buildDB` needs the (empty) database named in `db.production.datasource.databaseName` to exist and be owned by the database user. `:dbmodel:createUserDB` does the same for the userprofile database.
8. **Look inside the war** and compare it with the one of your old build (`unzip -Z1 webapp/build/libs/webapp.war | sort`):

   ```bash
   W=webapp/build/libs/webapp.war
   unzip -Z1 $W | grep '^WEB-INF/lib/' | sed -E 's#WEB-INF/lib/##; s/-[0-9][0-9A-Za-z._-]*\.jar$//' | sort | uniq -d   # nothing
   unzip -Z1 $W | grep -E '\.war$'                                                                                    # nothing
   unzip -p $W WEB-INF/lib/dbmodel.jar > /tmp/dbmodel.jar; unzip -Z1 /tmp/dbmodel.jar | grep -x intermine.properties # nothing
   unzip -Z1 /tmp/dbmodel.jar | grep -Ex 'default.intermine.properties|userprofile_model.xml|so.obo'                  # nothing
   unzip -Z1 $W | grep -E '^WEB-INF/(web.xml|struts-config.xml|tiles-defs.xml|aspects.xml|webconfig-model.xml)$'       # all five
   ```

   ```bash
   unzip -Z1 $W | grep -E 'commons-lang'            # commons-lang3-3.21.0.jar only (no commons-lang-2.6.jar unless you declared it)
   unzip -p $W dataCategories.jsp | cmp - webapp/src/main/webapp/dataCategories.jsp   # no output: the mine's own file won over bio-webapp's
   ```

   Both converted mines gave 132 jars in `WEB-INF/lib` (HumanMine's war is 68.5 MB with JDK 17 and JDK 8, FlyMine's 68.4 MB; the 134 of the first conversions included `stringtree-json` and `libreadline-java`, which InterMine no longer ships), no output from the first three commands (and none from the fourth, which came with the `build/im` layout of the plugin, see [BUILDING.md](BUILDING.md); checked on HumanMine), and the same file list whether the war was built straight from a clean tree, after `buildDB`, or again after `rm -rf webapp/build`. `dbmodel.jar` has no `intermine.properties` because the plugin excludes it from the `jar` task of every project that applies one of its dbmodel plugins (you need no exclude of your own); `WEB-INF/classes/intermine.properties` and `WEB-INF/web.properties` in the war do contain the database credentials, as before.

### What the plugin's task wiring changes in a mine

Since the task-output work of the 6.0 line the plugin's copy, key, model and Ant tasks declare their inputs and outputs, and each writes a directory of its own, `build/im/<task>` ([BUILDING.md](BUILDING.md#files-that-the-plugins-tasks-write-buildimtask) has the table), instead of writing into `build/resources/main`, which is now only what `processResources` puts there. A mine that has followed the steps above does not have to edit anything for it (the `maint-2026` branches of HumanMine and FlyMine build their wars with this plugin without a change to their build files). What a deployer sees, and the places where a mine's own build file or scripts can be affected:

- **Run `./gradlew clean` once after the mine takes the new plugin** (or `rm -rf dbmodel/build webapp/build`). An earlier plugin left `genomic_model.xml`, `genomic_keyDefs.properties`, `default.intermine.properties`, `so.obo` and `userprofile_model.xml` in `dbmodel/build/resources/main`, and the jar packs that directory: from an old build tree it would pack those files again next to the merged model of `build/im/mergeModels`, and `:dbmodel:jar` stops with `Entry genomic_model.xml is a duplicate but no duplicate handling strategy has been set`. Nothing else is needed: after the one `clean` the build tree has only the new layout. (A fresh checkout, as in step 7, has no old files.)
- **The merged model is in `dbmodel/build/im/mergeModels/<modelName>_model.xml`** (`genomic_model.xml` for `modelName = "genomic"`), not in `dbmodel/build/resources/main/`. `mergeModels` copies the model of `bio-model` there and merges the additions of `project.xml`'s sources, the SO terms and the mine's resources into it; the directory is an output directory of the `main` source set (built by `mergeModels`), so `dbmodel.jar` still carries the merged model and the webapp still reads it from the jar. **A mine with its own `doLast` on `mergeModels`**, or any script that reads or edits `dbmodel/build/resources/main/genomic_model.xml`, must point at the new file (`layout.buildDirectory.file('im/mergeModels/genomic_model.xml')`); at the old path it finds nothing, or a stale file. `copyGenomicModel` is kept as an alias of `mergeModels`, so a build file that names it still works.
- **`dbmodel.jar` carries less.** It has the merged model and the mine's own resources, and no longer `default.intermine.properties`, `so.obo`, `userprofile_model.xml` or the keys file that `generateKeys` writes (`genomic_keyDefs.properties`; the mine's own `dbmodel/resources/genomic_keyDefs.properties` is a resource of the mine and stays in). Before, those files were in the jar if the tasks that wrote them had happened to run before it. The webapp has its own `default.intermine.properties`: `webapp`'s `copyDefaultInterMineProperties` feeds `processResources`, so the war always has it in `WEB-INF/classes`, next to `intermine.properties` and the mine's own resources. Code of the mine that read one of these files from the `dbmodel` jar on the class path must read it from `WEB-INF/classes` (the properties) or, for `so.obo`, from the `bio-core` jar (the `so` source's working copy is `build/im/integrate/so.obo`).
- **The Ant tasks always prefer the keys file of `generateKeys`** (`build/im/generateKeys/genomic_keyDefs.properties`, the keys of every source in `project.xml`) to a file of the same name in the mine's resources, as they did when `generateKeys` overwrote the copy in `build/resources/main`. The plugin puts `build/im/*` first on the classpath of every Ant task. To change the keys of a source, change that source's keys, not the file in `dbmodel/resources`, which the integration does not read.
- **`bio-model` carries its own `default.intermine.properties`.** `bio-model-<version>.jar` has the `src/main/resources/default.intermine.properties` of the bio-model project (with the `ensembl-human` data source). Earlier builds sometimes had a copy of the bio-test file (2009 bytes instead of 2289) in it, depending on which tasks ran before the jar. The jars of the `bio-source-*` and `bio-postprocess-*` modules no longer contain `genomic_model.xml`, `global_additions.xml`, `default.intermine.properties`, the key or priority files either (they did, depending on task order).
- **Logging of the Ant tasks.** `buildDB`, `integrate`, `postProcess` and the model and key tasks print their log4j output again: before, every logger printed `log4j:WARN No appenders could be found` and the INFO messages of the converters, the data loader and `buildDB` were lost. How the plugin does it (the file `build/im/antLogging/log4j.properties`, the levels that follow `-i`, `-q` and `-d`, `log4j.ignoreTCL`) is in [BUILDING.md](BUILDING.md#logging-of-the-plugins-ant-tasks); it is not repeated here. What a mine can do: a `log4j.properties` or `log4j.xml` in its resources (it is looked for in `build/resources/main`, so it counts from the build after `processResources` has copied it) is kept and the plugin then writes none (`buildDB` was checked with one: its lines come out in the mine's own format; `createSoModel` of a mine does not have the build directory on its class path and then prints the `No appenders` warning, a known gap); an explicit `-Dlog4j.configuration=file:/path/log4j.properties` on the Gradle command line still takes precedence over both, because log4j reads that property first (tried with `createSoModel`). The file is not an output of any task, so a different log level does not make `buildDB` or `integrate` run again.
- **The acceptance test report and the update trigger files moved.** `runAcceptanceTests` writes `acceptance_test.html` to `dbmodel/build/im/runAcceptanceTests/`, and `generateUpdateTriggers` writes `add-update-triggers.sql`, `remove-update-triggers.sql` and `key-checker.sql` to `dbmodel/build/im/generateUpdateTriggers/`; they used to go into `build/resources/main`, so that the dbmodel jar and the war carried the files of the last run. Each task empties its directory first and always runs (the database is no input).
- **Up to date.** Because the tasks declare outputs, a second `./gradlew :webapp:war` with nothing changed executes only `summariseObjectStore` (it reads the database and is never up to date), and `buildDB` and the other tasks that create tables write into `build/tmp/intermine-buildDB` (up to 5.1 they emptied Torque's temporary directory, `build/tmp`), so they no longer make every `compileJava` and `jar` run again.

### Worked example: HumanMine

The commit `build: convert HumanMine build files to the InterMine 6.0 line` of the HumanMine repository (now on the `maint-2026` branch of https://github.com/gmicklem/humanmine, commit `f426ac8`; later commits there moved the war composition and the SO paths to the plugin), abbreviated. The wrapper is its own commit. FlyMine's is the same with different line numbers; its differences are below.

```diff
--- gradle.properties
-systemProp.imVersion=5.0.+
-systemProp.bioVersion=5.0.+
+systemProp.imVersion=6.0.+
+systemProp.bioVersion=6.0.+

--- build.gradle
 buildscript {
     repositories {
         mavenLocal()
         mavenCentral()
+        gradlePluginPortal()  // gradle-cargo-plugin 2.9.0 is not on Maven Central
     }
     dependencies {
-        classpath 'com.bmuschko:gradle-cargo-plugin:2.8.0'
+        classpath 'com.bmuschko:gradle-cargo-plugin:2.9.0'
 subprojects {
     apply plugin: 'java'
-    apply plugin: 'maven'
-    sourceCompatibility = 1.8
-    targetCompatibility = 1.8
+    java {
+        sourceCompatibility = JavaVersion.VERSION_1_8
+        targetCompatibility = JavaVersion.VERSION_1_8
+    }
+    if (JavaVersion.current().isJava9Compatible()) {
+        tasks.withType(JavaCompile).configureEach { options.release = 8 }
+    }
     repositories {
         mavenLocal()
-	mavenCentral()
-        jcenter()
-        maven { url 'https://clojars.org/repo' }
+        mavenCentral()
+        maven { url = 'https://clojars.org/repo' }     // same for the ebi-repo URL
     dependencies {
-        compile group: "org.intermine", name: "intermine-resources", version: System.getProperty("imVersion")
-        compile('ant:ant:1.6.5')
-        testCompile group: 'junit', name: 'junit', version: '4.8.2'
+        implementation group: "org.intermine", name: "intermine-resources", version: System.getProperty("imVersion")
+        testImplementation group: 'junit', name: 'junit', version: '4.8.2'
     }
     task copyMineProperties(type: Copy) {
-    description "Copies mine specific ..."
-    dependsOn 'processResources'
-    from (minePropertyFile)
-    into sourceSets.main.output.resourcesDir
-    rename { fileName -> fileName.replace("$minePropertyFileName", "intermine.properties") }
-    inputs.sourceFiles.stopExecutionIfEmpty()
-  }
+        description = "Copies mine specific ..."
+        from (minePropertyFile)
+        into layout.buildDirectory.dir('im/mine-properties')
+        rename { fileName -> fileName.replace("$minePropertyFileName", "intermine.properties") }
+    }
+    processResources {
+        from copyMineProperties
+    }

--- dbmodel/build.gradle
-    compile group: "org.intermine", name: "intermine-integrate", ...      # also intermine-resources, bio-core, fileTree(libs)
+    implementation group: "org.intermine", name: "intermine-integrate", ...

--- webapp/build.gradle
 apply plugin: "war"
-apply from: "https://raw.githubusercontent.com/akhikhl/gretty/master/pluginScripts/gretty.plugin"
 apply plugin: "webapp"
-    compile (project(":dbmodel")) {transitive = false}          # and the four org.intermine lines
+    implementation (project(":dbmodel")) {transitive = false}
-war {
-    dependsOn 'mergeProperties', 'summariseObjectStore', 'unwarBioWebApp', 'addStrutsConfig'
-    finalizedBy 'warWebApp'
-}
-task warWebApp(type: War) {       // the plugin's war takes the exploded bio-webapp, src/main/webapp and the properties itself
-    ...
-}
-task copyWebappContent(type: Copy) {
-    from "src/main/webapp/"
-    into "${explodedWebAppDir}"
-}
-gretty {
-    jvmArgs = ['-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true']
-    contextPath = props.getProperty("webapp.path")
-}
 ...
-    main = "bluegenes.core"
+    mainClass = "bluegenes.core"
```

Why `copyMineProperties` changed: with the old form (`into sourceSets.main.output.resourcesDir`, `dependsOn 'processResources'`) a build that runs `:webapp:copyMineProperties` and `:webapp:jar` fails on Gradle 8 with `Task ':webapp:jar' uses this output of task ':webapp:copyMineProperties' without declaring an explicit or implicit dependency`, because the file lands in a directory that `jar` reads. Keep the task and its name (the plugin's `mergeProperties` and the Ant tasks find the file through it), but let `processResources` take the file from the task's own directory, so that every reader of `build/resources/main` depends on it through its inputs. The plugin's `mergeProperties` task takes the resources directory as the output of `processResources` and of `copyMineProperties`, so nothing else is needed. (Plugin builds before this change, the 5.1.1 release candidate included, listed only the copy task as the producer of `intermine.properties`: `:webapp:war` then failed with `Task ':webapp:mergeProperties' uses this output of task ':webapp:processResources' without declaring an explicit or implicit dependency` unless the mine added `tasks.named('mergeProperties') { inputs.files(tasks.named('processResources')) }`.)

**FlyMine differences.** `imVersion=5.1.+` to the same value as above; the build files are indented by two spaces and have a `props.getProperty("webapp.port")`/`webapp.protocol` block in `cargo { }` (nothing to change); `dbmodel/build.gradle` has the same four `compile` lines in another order and an `extraModelsEnd` with two more additions files; the `blueGenesStart` task of FlyMine also sets `bluegenes-tool-path`. The 54 sources of FlyMine's `project.xml` need 19 jars from `flymine-bio-sources` (see above). With them the result is the same: `:dbmodel:buildDB` (tables in the production database), `:webapp:war` from a clean tree, 134 jars in `WEB-INF/lib`, none repeated.

### What breaks, and how it looks

The messages below were produced by running HumanMine at its upstream master on Gradle 8.14.6 and fixing one error after the other (the `gretty` and `cargo` lines separately), roughly in the order that a mine meets them:

| Message | Cause and fix |
|---|---|
| `Plugin with id 'maven' not found.` | delete `apply plugin: 'maven'` (step 3) |
| `Could not find method compile() for arguments [...] on object of type DefaultDependencyHandler` | `compile`, `testCompile` and `runtime` were removed in Gradle 7: `implementation`, `testImplementation`, `runtimeOnly` (also `compile(...)` with parentheses) |
| `No signature of method: ...TaskInputUnionFileCollection.stopExecutionIfEmpty() is applicable for argument types: () values: []` | delete the line (Gradle 5.0 removed the method); an empty `Copy` is skipped as `NO-SOURCE` by itself |
| `Could not resolve org.akhikhl.gretty:gretty:2.0.0 ... Could not GET 'https://jcenter.bintray.com/...' Received status code 403` (line 2 of `webapp/build.gradle`) | the gretty `apply from`; delete it and the `gretty { }` block (step 5) |
| `Could not find com.bmuschko:gradle-cargo-plugin:2.9.0 ... Searched in ... repo.maven.apache.org` | add `gradlePluginPortal()` to the `buildscript` repositories, or stay on 2.8.0 |
| `Could not find file containing SO terms to add to the model, check the dbmodel/build.gradle file. Property was: <gradle home>/daemon/8.14.6/dbmodel/resources/so_terms` | relative `soTermListFilePath` / `soAdditionFilePath` with a plugin from before the fix of im-ee7.47: use the current plugin, or write the paths as `"$rootDir/dbmodel/..."` (step 4) |
| `A problem was found with the configuration of task ':webapp:jar' ... uses this output of task ':webapp:copyMineProperties' without declaring an explicit or implicit dependency` (also `:webapp:mergeProperties` ... `:webapp:processResources`) | the old `copyMineProperties`; see the paragraph after the example |
| `Entry genomic_model.xml is a duplicate but no duplicate handling strategy has been set` (task `:dbmodel:jar`) | `dbmodel/build/resources/main` still holds files of an earlier plugin: run `./gradlew clean` once ("What the plugin's task wiring changes in a mine") |
| `Entry model/images/favicon.ico is a duplicate but no duplicate handling strategy has been set` (task `:webapp:warWebApp`) | a `warWebApp` of your own: delete it, the plugin's `war` has `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` (step 5) |
| a war of 46 MB without `WEB-INF/web.xml`, `struts-config.xml` or the bio-webapp libraries, and no error; `build/explodedWebApp` holding only the mine's own files | `copyWebappContent` emptied the directory on its first run: delete the task (step 5); the plugin's `war` takes `src/main/webapp` itself |
| `Could not find org.intermine:bio-source-redfly:9.9.9` in `:dbmodel:generateKeys` / `:dbmodel:mergeModels` | the source's jar is not in any repository: publish the mine's source repository (see above), or the `version` in `project.xml` is wrong |
| `error: cannot find symbol  import org.intermine.model.bio.MicroArrayAssay;` in `:webapp:compileJava` | the model was generated from fewer sources than `project.xml` lists; restore the sources, delete `dbmodel/build` (or `./gradlew :dbmodel:clean`) |
| `Properties should be assigned using the 'propName = value' syntax ...` (Gradle 10); `The JavaExecSpec.main property has been deprecated` (Gradle 9); `The org.gradle.api.plugins.JavaPluginConvention type has been deprecated` | `url = ...`, `description = ...`, `mainClass = ...`, the `java { }` block (steps 3 and 5); they are warnings in 8.14 |
| `Executing Gradle on JVM versions 16 and lower has been deprecated` | the build runs on JDK 8; use JDK 17 for Gradle (the mine's jars and war still run on JDK 8) |
| `ant-1.6.5.jar` and `ant-1.10.x.jar` (plugins up to the 5.1.1 release candidate; 6.0 leaves the first out and warns `The war leaves out ant-1.6.5.jar` when the mine itself still declares it; the 5.x POMs were the other source, 6.0 POMs do not carry it), `org.apache.oltu.oauth2.client-1.0.0.jar` next to `-1.0.1.jar` (and the same for `.common`; InterMine 5.1.1 builds), or `intermine-webapp-<version>.war` (12 MB), in `WEB-INF/lib` | `compile('ant:ant:1.6.5')` (step 3: delete it); the oltu jars: the POM of `intermine-webapp` 5.1.1 names 1.0.0 (a `providedCompile` 1.0.0 next to `api` 1.0.1 in its build file), which the mine's own classpath takes, while the `bio-webapp` war brings 1.0.1; the 6.0 line has 1.0.2 only, from the one `api` line; the war is in the POM of `intermine-webtasks` 5.1.0 (Maven Central) and no longer in those of `maint-2026` (a 6.0 build against a published 5.1.0 POM needs `classpath = classpath.filter { !it.name.endsWith('.war') }` on the `War` tasks) |
| `error: package org.apache.commons.lang does not exist` or `cannot find symbol  method escapeHtml(String)` / `escapeXml` / `equals` in your own Java | step 6: move to lang3 (`escapeHtml4`, `escapeXml10`, `Objects.equals`), or declare `commons-lang:commons-lang:2.6` yourself; InterMine no longer brings it |
| `Test of distribution url https://services.gradle.org/... failed` from `./gradlew wrapper` | a proxy that rejects the check; `--no-validate-url` (step 1) |

A mine on Tomcat 8 can stay on it: `containerId = 'tomcat8x'` in the `cargo { }` block does not depend on the JDK or Gradle version of the build.

## cglib is gone (6.0.0)

InterMine builds the class of an object that belongs to more than one model class (a `Company` that is also `Broke`, a `CEO` that is also `Broke`; single-class objects use the generated classes, `CompanyShadow` and so on) at run time. Up to 5.1.1 it did that with cglib 3.1 (2014, unmaintained); 6.0.0 uses [Byte Buddy](https://bytebuddy.net) (`net.bytebuddy:byte-buddy`, a runtime dependency of `intermine-objectstore`, so it is in the war without a line in the mine). JDK proxies (`java.lang.reflect.Proxy`) were not enough, because such a class may have to extend a concrete model class (`is-interface="false"`, like `CEO` in the test model).

- `cglib:cglib` is no longer a dependency of any InterMine artifact, and `intermine-model` no longer brings it. A mine that imports `net.sf.cglib` itself must declare it, or better, stop using it: InterMine's objects no longer implement `net.sf.cglib.proxy.Factory`.
- A build that takes the InterMine jars from local directories without their POMs (the `intermine.development.*` properties, as InterMine's own testmine and bio builds do) gets no transitive dependencies and must declare `net.bytebuddy:byte-buddy:1.18.12` where it declared `cglib:cglib:3.1`; otherwise loading or reading an object of more than one class stops with `NoClassDefFoundError: net/bytebuddy/matcher/ElementMatcher`. A mine that resolves InterMine from Maven gets it through the POM of `intermine-objectstore`.
- In place of `net.sf.cglib.proxy.Factory`, a generated class implements `org.intermine.model.DynamicObject`. Code that tested `obj instanceof Factory` (for "is this an object of several classes?") tests `obj instanceof DynamicObject`, or better, `Util.decomposeClass(obj.getClass()).size() > 1`. `((Factory) obj).getCallback(0)` becomes `((DynamicObject) obj).dynamicHandler()` (still an `org.intermine.util.DynamicBean`), and `setCallback(0, new DynamicBean())` after `newInstance()` is not needed any more: the constructor of a generated class creates its `DynamicBean`.
- The class names changed from `org.intermine.model.bio.Gene$$EnhancerByCGLIB$$1a2b3c` to `org.intermine.model.bio.Gene_Protein$$Dynamic` (the model classes in name order). Nothing in InterMine reads these names (`Util.getFriendlyName`, `DynamicUtil.getSimpleClass` and the webapp go through `Util.decomposeClass`); code of your own that parsed `$$EnhancerByCGLIB$$` out of a class name should call `Util.decomposeClass` instead. The classes are loaded by a class loader of their own, a child of the one that loaded the model, so `Class.forName` on such a name does not find them.
- Stored data is not affected: the database keeps the names of the model classes, never the name of a generated class. A stored object that names `net.sf.cglib.proxy.Factory` among its classes (InterMine never wrote one) is still read; the name is skipped.

## commons-lang 2 is gone (6.0.0)

Up to 5.1.1 `commons-lang:commons-lang:2.6` was in every mine's `WEB-INF/lib` and on its compile classpath, although no InterMine class imports it (every file that uses commons-lang uses `commons-lang3`). It arrived by two routes, and 6.0.0 closes both:

- **`solr-solrj` 8.11.4.** Its POM lists commons-lang 2.6 as a compile dependency. `intermine-api` and `intermine-webapp` (and `bio-postprocess`) now exclude `commons-lang:commons-lang` where they declare solrj; solrj does not use it for what InterMine calls (`HttpSolrClient`, `SolrQuery`, the schema requests), and the unit tests and the testmine run without it.
- **The vendored Torque jars of `intermine-objectstore`** (`torque-3.2-dev.jar`, `torque-gen-3.2-dev.jar`). `SchemaType`, `Domain` and `Column` extend `org.apache.commons.lang.enum.Enum`, `TorqueException` extends `lang.exception.NestableException`, and several classes call `lang.StringUtils`; lang3 has none of these. Two places in InterMine loaded a Torque class: `BuildDbTask`, the DDL task of the build (`buildDB`, `buildUnitTestDB`, and the tables of `integrate`), and the SQL generator, which looked up the PostgreSQL type of a `QueryCast` through `SchemaType` and `Platform`, so every webapp needed lang 2. The SQL generator now holds the eight casts itself (`SMALLINT` is `INT2`, `BIGINT` is `INT8`, `LONGVARCHAR` is `TEXT`, `BIT` is `BOOLEAN`, `INTEGER`, `REAL`, `DOUBLE PRECISION`, `NUMERIC`, the same SQL as before: PostgreSQL is the only supported database), so it no longer loads Torque. `BuildDbTask` no longer uses Torque either: the Torque jars are gone ([Torque is gone](#torque-is-gone-600)), and no plugin configuration holds commons-lang 2.6 for that Ant task any more (a development build of `maint-2026` had one, `imTorqueRuntime`; no release did). commons-lang 2.6 is in no POM and in no `runtimeClasspath` of a mine. `intermine-objectstore`'s POM no longer lists it, nor do those of `bio-core`, `bio-model`, `bio-postprocess` and the bio-sources.

**What a deployer does.** A mine's own Java that imports `org.apache.commons.lang.*` stopped compiling in 6.0.0 (`error: package org.apache.commons.lang does not exist`). Replace it with `org.apache.commons.lang3` (step 6 of [the 6.0.0 list](#steps)): the package name changes, `StringUtils`, `ArrayUtils` and most of `ObjectUtils` are the same calls, `StringEscapeUtils.escapeHtml` is `escapeHtml4` (`escapeXml` is `escapeXml10`, `unescapeHtml` is `unescapeHtml4`), `Validate` throws `NullPointerException` instead of `IllegalArgumentException` for `null` and has `String, Object...` messages. `WordUtils` is in `org.apache.commons.lang3.text` (deprecated, still shipped in 3.21.0); its successor is commons-text (`org.apache.commons.text.WordUtils`), which InterMine does not ship, so a project that uses it declares `org.apache.commons:commons-text` itself. A bio-source project that takes `bio-core` with `transitive: false` declares `commons-lang3` itself (HumanMine's and FlyMine's source repositories do: `implementation libs.commons.lang3`). If you cannot move yet, declare `implementation 'commons-lang:commons-lang:2.6'` in your own build file; it then goes into your war and nowhere else.

**Check it.** `./gradlew :webapp:dependencies --configuration runtimeClasspath | grep commons-lang` shows `commons-lang3` only, and `unzip -Z1 webapp/build/libs/webapp.war | grep commons-lang` shows `commons-lang3-3.21.0.jar`. InterMine's own testmine does the same check in its build: `testmine/webapp/build.gradle` has a `checkNoCommonsLang2` task, run by `check`, that fails when a `commons-lang-2.x.jar` is in the war or on the runtime classpath; a mine can copy it.

## Torque is gone (6.0.0)

Up to 5.1.1 the tables of a mine's databases were made by Apache Torque 3.2, in vendored and locally patched jars packed into `intermine-objectstore` (`torque-3.2-dev.jar`, `torque-gen-3.2-dev.jar` and `velocity-1.3.jar`, from 2005; they needed commons-lang 2 and commons-logging). Two Ant tasks did it: `TorqueModelOutputTask` wrote a Torque schema (`build/<objectstore>-schema.xml`, for example `os.production-schema.xml`) from the model and the objectstore's properties, and `BuildDbTask` had torque-gen turn it into SQL through Velocity templates and ran it. In 6.0.0 `BuildDbTask` makes the SQL itself, with `org.intermine.objectstore.intermine.DdlGenerator`, straight from the model and the same properties (`os.<name>.model`, `truncatedClasses`, `noNotXml`, `missingTables`).

**The database is the same.** The generator writes the statements that Torque's PostgreSQL templates wrote: the same tables, columns in the same order, types (`INTEGER`, `INT2`, `INT8`, `TEXT`, `BOOLEAN`, `REAL`, `DOUBLE PRECISION`, `NUMERIC`, `BYTEA`), `NOT NULL`, the `<table>_pkey` unique constraints, the indirection tables of many-to-many collections, `intermine_metadata`, `osbag_int` with its index and `clob`. This was checked on the testmodel objectstores (`unittest`, truncated, with the serialised objects, flat mode), `userprofile`, `fulldata` and two `genomic` models (bio-model's and HumanMine's, 243 tables): the statements are the same and `pg_dump --schema-only` of a database made by each is identical. Torque also wrote a `DROP TABLE ... CASCADE` before each `CREATE TABLE` and comment banners; they are gone (`BuildDbTask` empties the database first, as before). `buildDB`, `buildUnitTestDB`, `createUserDB` and the items databases of `integrate` behave as before, and a database made by 5.1 needs no change.

**What a deployer does.** Nothing, for a mine that uses the plugin's tasks: plugin 6.0.0 calls the new `BuildDbTask` (it must be used with InterMine 6.0.0, as the [version table](#which-versions-go-together) says). A mine's own build file or script needs an edit only if it called the Ant tasks itself:

- **`TorqueModelOutputTask`** (Ant name `torque`, `build-torque` in `antlib-os.xml`) is removed. Delete the `ant.taskdef(name: 'torque', ...)` and `ant.torque(...)` calls; nothing replaces them.
- **`BuildDbTask`** (`build-db`, `buildDB`): keep `osName` and `model`. `schemafile` is ignored (with a warning) and can be dropped. `tempDir` is optional: if it is given, the task writes the SQL it runs there, as `<osName>-schema.sql`, and no longer empties the directory.
- The classpath of `BuildDbTask` now needs what `TorqueModelOutputTask`'s had: `intermine.properties`, the model XML and the compiled model classes. The plugin's `createTables` has them; a hand-written task usually gets them from `runtimeClasspath` plus the project's build directory.
- **A configuration or dependency that existed for Torque** can go: a commons-lang 2.6 that your own build file put on the Ant classpath of `buildDB`, and `commons-logging`, which nothing else of InterMine uses (it is no longer in the POMs of `intermine-objectstore`, `bio-core` and the bio-sources; declare it if your own code imports `org.apache.commons.logging`). Do not exclude it from your mine's `dbmodel` either, and do not add `jcl-over-slf4j` to replace it: the war has both whatever the `dbmodel` says (the testmine war, built with this release, has `commons-logging-1.3.5.jar` and `jcl-over-slf4j-1.7.36.jar` in `WEB-INF/lib`). `commons-logging` arrives through `intermine-webapp` (Struts 1.3.10 and `commons-beanutils`, `commons-digester` and `commons-validator` need it; `./gradlew :intermine-webapp:dependencyInsight --dependency commons-logging --configuration runtimeClasspath`), `jcl-over-slf4j` through `solr-solrj` (which `dbmodel` has). Both provide `org.apache.commons.logging` and both end in reload4j (commons-logging finds log4j 1.x itself, `jcl-over-slf4j` goes through `slf4j-reload4j`), so which jar the class loader finds first does not matter.
- The `<objectstore>-schema.xml` files are no longer written. A script that read them can read `build/tmp/intermine-buildDB/<objectstore>-schema.sql` (the SQL) instead, or query the database.

## Publishing and dependencies of the InterMine artifacts

- The old OSSRH and `uploadArchives` publishing is gone from InterMine itself (see [RELEASING.md](RELEASING.md)); a mine only consumes the released artifacts from Maven Central.
- With Gradle 5 and later, Gradle uses the POM's compile and runtime scopes separately, so a mine that compiles against a class that comes through an InterMine artifact depends on that artifact declaring it as `api`/compile. The planning notes expect a `package ... does not exist` error in a mine as the symptom if an artifact declared something as `implementation` where `api` was needed. The next section is the case that matters for mines: the `bio-source-*` artifacts.
- The plugin's internal configurations were renamed (`api` to `imApiJar`, `antlr` to `imAntlr`, `plugin` to `imPlugin`). Mines do not use them (checked in humanmine and flymine).
- The Ant tasks that the plugin runs (integrate, buildDB, postprocess) now take their classpath from `runtimeClasspath` instead of `compile`; this is a superset (it also contains `runtimeOnly` dependencies), and the order of the jars can differ.

## Dependency scopes in the bio-source POMs

In the 5.1.0 release every dependency of a `bio-source-*` artifact was in the POM with scope `compile`. In 5.1.1 and 6.0.0 most are `runtime`: the `bio/sources` build declares them as `implementation` (it uses `api` only for types that appear in the converters' constructors and protected methods; see the comment in `bio/sources/build.gradle`), and the POM maps `implementation` to `runtime`. `bio/postprocess` has no `api` at all, so its POMs are `runtime` throughout. The mapping is described in [BUILDING.md](BUILDING.md).

For a `bio-source-*` POM of this tree the scopes are:

| Scope | Dependencies |
|---|---|
| `compile` | `bio-core`, `intermine-objectstore`, `intermine-model`, `ant`, `commons-collections`, `reload4j`, `slf4j-reload4j`, in most sources `intermine-integrate`, and what a source adds for its own API (for example `biojava-core` in `bio-source-fasta`) |
| `runtime` | `HikariCP`, `intermine-resources`, `commons-io`, `commons-lang3`, `postgresql`, `jakarta.activation-api`, and `bio-model` in the sources that list it (`commons-logging` was there until the Torque jars went, [Torque is gone](#torque-is-gone-600)) |

Nothing changes for the plugin: it puts the bio-source jars on a runtime classpath, where both scopes arrive. It matters for a mine's own bio-source project (your `*-bio-sources` repository) that **compiles against classes of an `org.intermine` bio-source jar**, for example a processor that extends a class of `bio-source-chado-db`. Gradle gives such a project only the `compile` entries of that POM. A library that your code imports, and that used to arrive through the bio-source jar, may now be missing at compile time, and the build stops with `error: package org.apache.commons.logging does not exist` (or `org.postgresql`, `com.zaxxer.hikari`, `javax.xml.bind`).

What to do:

- Declare every `org.intermine` bio-source jar that your classes use, with the same version as before. It is `implementation`, or `api` if its types appear in your own public signatures. This always was necessary, but it used to be hidden when the jar came in through another dependency.
- Declare every library whose classes you import yourself, instead of relying on what a POM brings in. Use the versions of InterMine's catalog (`gradle/libs.versions.toml` in the InterMine repository) so that your war carries one copy:

  ```groovy
  dependencies {
      implementation 'org.intermine:bio-source-chado-db:6.0.+'
      implementation 'commons-logging:commons-logging:1.3.5'
      implementation 'org.postgresql:postgresql:42.7.14'
  }
  ```

- `commons-io` and `commons-lang3` are `compile` dependencies of `bio-core`, which a mine's bio-source project declares anyway, so they stay on your compile classpath (with `transitive: false` on `bio-core`, declare `commons-lang3` yourself). **`commons-lang` 2.6 is in none of these POMs any more** (it used to be `runtime` in those of `bio-core`, `bio-model`, the bio-sources and `intermine-objectstore`, and it compiled by accident through `solr-solrj`'s POM): use `org.apache.commons.lang3`, or declare `implementation 'commons-lang:commons-lang:2.6'` yourself ([commons-lang 2 is gone](#commons-lang-2-is-gone-600)).
- To see what a POM gives you: `./gradlew dependencies --configuration compileClasspath` in your project, or the `<scope>` entries of the POM on Maven Central.

## Removed from the webapp

- **OpenID 2.0 login (6.0.0).** The `/openid` servlet (`OpenIDAuthenticator`), its `openid-providers.properties` list, the "using OpenID" block of the login page and the `JOpenId` library are gone. OpenID 2.0 providers shut the protocol down years ago (Google in 2015), and the shipped provider list was empty, so the login page never offered it. OAuth2 / OpenID Connect login (`oauth2.*` properties) is unchanged, including the migration of old Google OpenID accounts. A mine that has its own `openid-providers.properties` or an `openid-auth` servlet entry in its `web.xml` can delete them; the `openid.allowed` web property is no longer read.
- **Bouncy Castle (6.0.0).** `bcprov-jdk15on` is no longer a dependency; the certificate that `KeyStoreBuilder` stores for each `security.publickey.*` entry is written with the JDK classes. The signing algorithms are the SHA-1/224/256/384/512 variants of RSA and ECDSA.
- **Xalan (6.0.0).** `xalan:xalan` is no longer a dependency of `intermine-api`. No InterMine class, JSP, tag or script applies an XSLT stylesheet (the webapp has no `.xsl` file, no `TransformerFactory` and no JSTL `x:transform`; the only `javax.xml.transform` import is `StreamSource`, the input type of the XML Schema validation of uploaded XML queries and of the aspects and web configuration files, which the JDK's own `javax.xml.validation` does). A mine that applies stylesheets itself uses the JDK's XSLTC (`TransformerFactory.newInstance()`), or declares `xalan:xalan` (2.7.3 has the fix for CVE-2022-34169). The unused `config/checkstyle/checkstyle-frames.xsl`, a report stylesheet of the old Ant checkstyle task that nothing referenced, is deleted.

## Changed in the webapp

- **Registry lookup has timeouts (6.0.0).** With `markup.webpages.enable=true` the home page asks the InterMine registry (`registry.intermine.org`) which instance this mine is. The request had no timeout, so a registry that accepted the connection and never answered hung the page. It now gives up after 5 s to connect and 10 s to read (`markup.registry.connectTimeout` and `markup.registry.readTimeout` in `web.properties`, milliseconds), logs one warning, and renders the markup without the registry data (the identifier is the mine's URL, no maintainer e-mail). After a failed lookup, or an answer that the mine is not registered, the registry is not asked again for 300 s (`markup.registry.retryInterval`, seconds); a successful lookup is kept for the life of the webapp, as before.
- **DWR servlet settings (6.0.0).** In the `web.xml` that InterMine ships, the `dwr-invoker` servlet no longer runs with `debug=true` (the `/dwr/` index and test pages listed every exposed class and ran any method from a browser form), `allowGetForSafariButMakeForgeryEasier=true` (GET calls are what a forged cross-site request needs; the DWR client only uses GET for Safari older than 1.3) or `activeReverseAjaxEnabled=true` (nothing in the webapp uses reverse Ajax). `allowScriptTagRemoting=false` and `crossDomainSessionSecurity=true`, the DWR defaults, are now written out. `/dwr/index.html` and `/dwr/test/*` now answer 501 Not Implemented (what DWR sends with `debug` off), a GET call is refused with `GET Disalowed`, and the pages' own DWR calls (POST) are unchanged (checked in a browser against HumanMine on Tomcat 8.5 and JDK 8: `getSavedBagStatus`, `validateBagName`, `getToggledElements`). A mine that has its own copy of `web.xml` should take the same four values; a mine that wants the DWR test pages back on a development server sets `debug` to `true` there only.
- **DWR exposes only the `AjaxServices` methods that are listed (6.0.0).** `intermine/webapp/src/main/webapp/WEB-INF/dwr.xml` has an `<include method="..."/>` for every `AjaxServices` method that a page or script of the webapp calls; any other public method of the class is not reachable from the browser (DWR otherwise publishes all of them). A mine whose own JSPs or JavaScript call an `AjaxServices.<method>(...)` that is not in that list gets a JavaScript error (`AjaxServices.<method> is not a function`) when the page runs the call: add `<include method="<method>"/>` to `dwr.xml` (a mine that has its own copy of the file adds it there). To find the calls, run `grep -rhoE 'AjaxServices\.[A-Za-z]+' <mine>/webapp/src/main/webapp | sort -u` and compare with the list. The webapp's own test `DwrIncludesTest` does this check for InterMine's pages. The query builder and template pages call `AjaxServices.getContent` (the constraint value autocompleter), which was missing from the list in the first 6.0.0 builds and is included now; `js/historyBagView.js`, whose only function called a method `filterByTag` that has never existed in `AjaxServices`, is removed (the lists page filters by tag with `filterByUserTag` in `js/imdwr.js`).

See [BUILDING.md](BUILDING.md) for how the InterMine builds themselves are set up.
