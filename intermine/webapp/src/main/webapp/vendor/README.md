# Third-party and InterMine browser libraries served by the webapp

Until November 2026 the webapp loaded these files from `https://cdn.intermine.org`
(`head.cdn.location` in `global.web.properties`). That host is gone, so the exact
files the webapp referenced are bundled here and served from `<webapp>/vendor/`.
A mine therefore needs no external CDN for its own pages, which also means it works
on an intranet.

The files are byte-for-byte copies of the `intermine/CDN` repository
(https://github.com/intermine/CDN, commit `fcaf61dfc2d46b9c16991a8f6f83dcf072388515`,
2021-02-08), using the same relative layout the CDN had. In `global.web.properties`
a head resource value starting with `CDN/` is resolved to `/vendor/` (see
`WebUtil.getHeadResources`), for example

    head.js.all.Ae = CDN/js/intermine/imjs/3.18.1/im.js    ->  <webapp>/vendor/js/intermine/imjs/3.18.1/im.js

`SHA256SUMS` lists the SHA-256 of every file (`sha256sum -c SHA256SUMS` from this
directory verifies the copy). Do not edit these files; to upgrade a library add the
new version next to the old one, update the property and extend `SHA256SUMS`.

| Library | Version | Path under `vendor/` | Used by |
|---|---|---|---|
| imjs (InterMine JS client) | 3.18.1 | `js/intermine/imjs/3.18.1/im.js` | every page (`head.js.all.Ae`, global `intermine.Service`) |
| im-tables | 2.2.0-beta | `js/intermine/im-tables/2.2.0-beta/` | results, report and list pages (`head.*.results.RESULT_TABLES`), generated JavaScript |
| intermine api-loader | latest | `js/intermine/api-loader/latest/intermine.api.min.js` | every page (`head.js.all.0`) |
| pomme.js | 0.2.6 | `js/intermine/pomme.js/0.2.6/app.min.js` | every page and the list upload confirmation (`head.js.all.POMME`, `component-400`) |
| list-widgets | 2.1.4 | `js/intermine/apps-c/list-widgets/2.1.4/app.min.js` | list analysis page |
| component-400 | 0.5.0 | `js/intermine/apps-c/component-400/0.5.0/` | list upload identifier-match confirmation page |
| pathways-displayer | 0.0.2 | `js/intermine/apps-c/pathways-displayer/0.0.2/` | report page (bio) |
| gene-interaction-displayer | 1.0.5 | `js/intermine/gene-interaction-displayer/1.0.5/` | report page (bio) |
| complexviewer | 1.0.4 | `js/complexviewer/1.0.4/`, `css/intermine/complexviewer/style.css` | report page (bio) |
| jQuery | 2.0.3 | `js/jquery/2.0.3/jquery.min.js` | every page |
| jQuery UI | 1.11.4 | `js/jqueryui/1.11.4/`, `css/jquery-ui/1.11.4/smoothness/` | complex viewer |
| Underscore | 1.7.0, 1.4.4 | `js/underscore.js/` | every page (1.7.0), pathways displayer (1.4.4) |
| Backbone | 1.1.2, 1.1.0 | `js/backbone.js/` | every page (1.1.2), pathways displayer (1.1.0) |
| Q | 0.9.7 | `js/q/0.9.7/q.min.js` | every page |
| async | 0.2.6 | `js/async/0.2.6/async.min.js` | list analysis page |
| FileSaver.js | 1.1.20151003, plus the unversioned `FileSaver.min.js` | `js/filesaver.js/` | list analysis page, im-tables export |
| d3 | 3.5.5, 3.0.6 | `js/d3/` | complex viewer (3.5.5), im-tables column summaries (3.0.6) |
| google-code-prettify | latest | `js/google-code-prettify/latest/prettify.js` | im-tables code generation dialog |
| Bootstrap | 2.0.4 prefixed no icons, 2.3.2 icons | `css/bootstrap/` | list analysis page, im-tables glyph icons |
| Font Awesome | 4.x (4.1.0) | `css/font-awesome/4.x/` (css and fonts) | every page |

## Known remaining external references

These are inside the vendored files, which are not modified:

* im-tables and imjs request `json3` from `cdn.intermine.org` only when the browser has no
  native `JSON` object (Internet Explorer 7 and older). Not supported anyway.
* The api-loader default load root and the im-tables default `CDN.server` still name
  `cdn.intermine.org`. The webapp overrides both on every page (`intermine.setLoadRoot`,
  `imtables.configure("CDN.server", ...)` in `htmlHead.jsp` and `layout.jsp`).
* `js/intermine/im-tables/2.2.0-beta/main.sandboxed.css` asks `netdna.bootstrapcdn.com` for the
  Glyphicons and Font Awesome fonts. `css/im-tables-fonts.css` (not vendored, part of the webapp, loaded
  after it by `head.css.results.RESULT_TABLES_FONTS`) redefines both font faces from `fonts/`.

## Fonts

`fonts/` holds the woff2, woff and ttf files of Font Awesome 4.7.0 (npm package `font-awesome@4.7.0`,
integrity `sha512-U6kGnykA/6bFmg1M/oT9EkFeIYv7JlX3bozwQJWiiLz6L0w3F5vBVPxHlwyX/vtNq1ckcpRKOB9f2Qal/VtFpg==`)
and Bootstrap 3.3.2 Glyphicons (npm package `bootstrap@3.3.2`, integrity
`sha512-4aP7n3MWfOyASkwezPN2Q7PBczCaaU/pD7sFA+1YlP8SVF4gjTQPbTKMeBopCg3S6ScUzmU64SFmWkE4Kx31kw==`),
the versions im-tables 2.2.0-beta was built against. The checksums are in `SHA256SUMS`.
