/*
 * Usage tracking charts for the superuser "tracks" tab of My Mine (track.jsp).
 *
 * These used to be drawn with the Google Visualization API, loaded from www.google.com/jsapi.
 * That URL now redirects to a different loader (google.charts.load), so the old google.load calls
 * no longer worked, and it made every page of the mine contact a third-party host. The charts are
 * now plain DOM tables (with a bar showing each row's share) and an inline SVG line chart, so
 * nothing is loaded from outside the mine.
 */
(function (root) {
    var SVG_NS = 'http://www.w3.org/2000/svg';
    var DAY_MS = 1000 * 60 * 60 * 24;
    var PAGE_SIZE = 10;

    function el(name, text, className) {
        var e = document.createElement(name);
        if (text !== undefined && text !== null) {
            e.appendChild(document.createTextNode(String(text)));
        }
        if (className) {
            e.className = className;
        }
        return e;
    }

    function clear(node) {
        while (node.firstChild) {
            node.removeChild(node.firstChild);
        }
    }

    function svgEl(name, attrs) {
        var e = document.createElementNS(SVG_NS, name);
        var k;
        for (k in attrs) {
            if (attrs.hasOwnProperty(k)) {
                e.setAttribute(k, attrs[k]);
            }
        }
        return e;
    }

    function formatDate(d) {
        var m = d.getMonth() + 1, day = d.getDate();
        return d.getFullYear() + '-' + (m < 10 ? '0' : '') + m + '-' + (day < 10 ? '0' : '') + day;
    }

    function calculateStartTime() {
        return new Date(new Date().getTime() - 30 * DAY_MS);
    }

    /** Line chart of [Date, count] rows for the last 30 days, as inline SVG. */
    function drawLineChart(container, title, rows) {
        var from = calculateStartTime().getTime(), to = new Date().getTime();
        var points = [], max = 0, i;
        for (i = 0; i < rows.length; i++) {
            var t = rows[i][0].getTime();
            if (t >= from && t <= to) {
                points.push([t, rows[i][1]]);
                max = Math.max(max, rows[i][1]);
            }
        }
        points.sort(function (a, b) { return a[0] - b[0]; });
        clear(container);
        if (points.length === 0) {
            container.appendChild(el('p', 'No ' + title + ' recorded in the last 30 days.'));
            return;
        }
        var W = 1000, H = 280, L = 50, R = 20, T = 15, B = 30;
        var svg = svgEl('svg', {width: W, height: H, viewBox: '0 0 ' + W + ' ' + H, role: 'img'});
        svg.appendChild(el('title', title));
        var x = function (t) { return L + (t - from) / (to - from) * (W - L - R); };
        var y = function (v) { return H - B - v / max * (H - T - B); };
        svg.appendChild(svgEl('line', {x1: L, y1: H - B, x2: W - R, y2: H - B, stroke: '#888'}));
        svg.appendChild(svgEl('line', {x1: L, y1: T, x2: L, y2: H - B, stroke: '#888'}));
        var label = function (attrs, text) {
            var tx = svgEl('text', attrs);
            tx.appendChild(document.createTextNode(text));
            svg.appendChild(tx);
        };
        label({x: L - 5, y: T + 4, 'text-anchor': 'end', 'font-size': 11}, max);
        label({x: L - 5, y: H - B + 4, 'text-anchor': 'end', 'font-size': 11}, 0);
        label({x: L, y: H - 10, 'text-anchor': 'start', 'font-size': 11}, formatDate(new Date(from)));
        label({x: W - R, y: H - 10, 'text-anchor': 'end', 'font-size': 11}, formatDate(new Date(to)));
        var d = '';
        for (i = 0; i < points.length; i++) {
            d += (i === 0 ? 'M' : 'L') + x(points[i][0]).toFixed(1) + ' ' + y(points[i][1]).toFixed(1);
        }
        svg.appendChild(svgEl('path', {d: d, fill: 'none', stroke: '#3366cc', 'stroke-width': 2}));
        for (i = 0; i < points.length; i++) {
            var dot = svgEl('circle', {cx: x(points[i][0]).toFixed(1), cy: y(points[i][1]).toFixed(1),
                                       r: 3, fill: '#3366cc'});
            dot.appendChild(el('title', formatDate(new Date(points[i][0])) + ': ' + points[i][1]));
            svg.appendChild(dot);
        }
        container.appendChild(svg);
    }

    /** Table of [name, tracks, users] rows, with a bar for each row's share of the tracks. */
    function drawTable(container, columnName, rows) {
        var shown = PAGE_SIZE;
        var total = 0, i;
        for (i = 0; i < rows.length; i++) {
            total += rows[i][1];
        }
        var render = function () {
            clear(container);
            if (rows.length === 0) {
                container.appendChild(el('p', 'Nothing recorded in this period.'));
                return;
            }
            var table = el('table', null, 'results');
            table.style.width = '600px';
            var head = el('tr');
            head.appendChild(el('th', columnName));
            head.appendChild(el('th', 'Tracks'));
            head.appendChild(el('th', 'Share'));
            head.appendChild(el('th', 'Users'));
            table.appendChild(head);
            for (var r = 0; r < rows.length && r < shown; r++) {
                var tr = el('tr');
                if (r % 2 === 1) {
                    tr.style.backgroundColor = '#f3f3f3';
                }
                tr.appendChild(el('td', rows[r][0]));
                tr.appendChild(el('td', rows[r][1]));
                var share = el('td');
                var pct = total > 0 ? Math.round(rows[r][1] / total * 100) : 0;
                var bar = el('span');
                bar.style.display = 'inline-block';
                bar.style.height = '10px';
                bar.style.width = Math.max(pct, 1) + 'px';
                bar.style.backgroundColor = '#3366cc';
                share.appendChild(bar);
                share.appendChild(document.createTextNode(' ' + pct + '%'));
                tr.appendChild(share);
                tr.appendChild(el('td', rows[r][2]));
                table.appendChild(tr);
            }
            container.appendChild(table);
            if (rows.length > shown) {
                var more = el('a', 'Show all ' + rows.length + ' rows');
                more.href = '#';
                more.onclick = function () {
                    shown = rows.length;
                    render();
                    return false;
                };
                container.appendChild(more);
            }
        };
        render();
    }

    root.drawTimeChart = function (trackId, title) {
        var container = document.getElementById('chart_div' + trackId);
        var draw = function (tracks) {
            drawLineChart(container, title, tracks || []);
        };
        if (trackId === 'template') {
            TrackAjaxServices.getTemplateTracksTrend(draw);
        } else if (trackId === 'query') {
            TrackAjaxServices.getQueryTracksTrend(draw);
        } else if (trackId === 'login') {
            TrackAjaxServices.getLoginTracksTrend(draw);
        } else if (trackId === 'search') {
            TrackAjaxServices.getSearchTracksTrend(draw);
        } else if (trackId === 'listExecution') {
            TrackAjaxServices.getListExecutionTrend(draw);
        } else if (trackId === 'listCreation') {
            TrackAjaxServices.getListCreationTrend(draw);
        }
    };

    root.drawTableChart = function (timeRange, trackId, columnName) {
        var container = document.getElementById('table_div' + trackId);
        var draw = function (tracks) {
            drawTable(container, columnName, tracks || []);
        };
        if (trackId === 'query') {
            TrackAjaxServices.getQueryTracksDataTable(timeRange, draw);
        } else if (trackId === 'search') {
            TrackAjaxServices.getSearchTracksDataTable(timeRange, draw);
        } else if (trackId === 'listExecution') {
            TrackAjaxServices.getListExecutionTracksDataTable(timeRange, draw);
        } else if (trackId === 'listCreation') {
            TrackAjaxServices.getListCreationTracksDataTable(timeRange, draw);
        }
    };

    // Template usage used to be a pie chart plus a table; the table's share column replaces the pie.
    root.drawPieAndTableChart = function (timeRange, trackId, columnName) {
        var container = document.getElementById('table_div' + trackId);
        TrackAjaxServices.getTemplateTracksPercentage(timeRange, function (tracks) {
            drawTable(container, columnName, tracks || []);
        });
    };

    root.calculateStartTime = calculateStartTime;
}(window));
