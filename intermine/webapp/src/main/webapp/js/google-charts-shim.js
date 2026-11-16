/*
 * A small local stand-in for the part of the Google Charts API that the list widgets app
 * (vendor/js/intermine/apps-c/list-widgets/2.1.4/app.min.js) uses, so that the list analysis page
 * does not load a script from www.gstatic.com.
 *
 * What the list widgets app needs from the global `google` (it reads the global once, when its
 * script runs, so this file has to be loaded before it):
 *
 *   google.load("visualization", "1.0", {packages: [...], callback: fn})   callback runs on the next tick
 *   google.visualization.ColumnChart | BarChart | PieChart                 the chart types a widget
 *                                                                          configuration can name
 *   new Chart(element).draw(dataTable, options)
 *   chart.getSelection()                                                   [{row, column}], [{row}] or [{column}]
 *   google.visualization.events.addListener(chart, "click" | "select", fn)
 *   google.visualization.arrayToDataTable(rows)                            rows[0] holds the column labels
 *
 * Any other chart type is absent, so the widgets app shows its own "chart type does not exist" message.
 * Options used from the Google API: width, height, colors, fontName, fontSize, hAxis.title, vAxis.title
 * and chartArea.top. The charts are plain inline SVG (no library); a bar, a pie slice or a legend entry
 * is selected by clicking it (click again to deselect), as in the Google charts.
 */
(function (root) {
    'use strict';

    var SVG_NS = 'http://www.w3.org/2000/svg';
    var DEFAULT_COLORS = ['#2F72FF', '#9FC0FF', '#4daf4a', '#ff7f00', '#984ea3', '#a65628', '#f781bf', '#999999'];
    var TEXT_COLOR = '#333';
    var GRID_COLOR = '#ddd';
    var AXIS_COLOR = '#888';
    var SELECT_COLOR = '#222';

    function svgEl(parent, name, attrs, text) {
        var e = document.createElementNS(SVG_NS, name);
        var k;
        for (k in attrs) {
            if (attrs.hasOwnProperty(k)) {
                e.setAttribute(k, attrs[k]);
            }
        }
        if (text !== undefined) {
            e.appendChild(document.createTextNode(text));
        }
        if (parent) {
            parent.appendChild(e);
        }
        return e;
    }

    function tooltip(node, text) {
        svgEl(node, 'title', {}, text);
    }

    function fmt(n) {
        if (n === Math.floor(n)) {
            return String(n);
        }
        return String(Math.round(n * 1000) / 1000);
    }

    function shorten(s, max) {
        s = String(s);
        return s.length > max ? s.substring(0, max - 1) + '…' : s;
    }

    // Rough text width, so that labels can be placed without measuring the DOM.
    function textWidth(s, fontSize) {
        return String(s).length * fontSize * 0.58;
    }

    // Axis maximum and tick step giving about five ticks, from zero.
    function niceScale(max) {
        var step, mag, norm;
        if (!(max > 0)) {
            return {max: 1, step: 0.2};
        }
        mag = Math.pow(10, Math.floor(Math.log(max / 5) / Math.LN10));
        norm = (max / 5) / mag;
        step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
        return {max: Math.ceil(max / step) * step, step: step};
    }

    /* ---- DataTable ---- */

    function DataTable(rows) {
        this.labels = [];
        this.rows = [];
        if (rows && rows.length) {
            this.labels = rows[0].map(String);
            this.rows = rows.slice(1);
        }
    }
    DataTable.prototype.getNumberOfRows = function () {
        return this.rows.length;
    };
    DataTable.prototype.getNumberOfColumns = function () {
        return this.labels.length;
    };
    DataTable.prototype.getColumnLabel = function (c) {
        return this.labels[c];
    };
    DataTable.prototype.getValue = function (r, c) {
        return this.rows[r][c];
    };

    function num(v) {
        v = Number(v);
        return isNaN(v) ? 0 : v;
    }

    /* ---- events ---- */

    var events = {
        addListener: function (chart, name, fn) {
            (chart._listeners[name] = chart._listeners[name] || []).push(fn);
            return {chart: chart, name: name, fn: fn};
        },
        removeListener: function (handle) {
            var l = handle.chart._listeners[handle.name] || [];
            var i = l.indexOf(handle.fn);
            if (i >= 0) {
                l.splice(i, 1);
            }
        },
        trigger: function (chart, name) {
            var l = (chart._listeners[name] || []).slice();
            var i;
            for (i = 0; i < l.length; i++) {
                l[i].call(chart);
            }
        }
    };

    /* ---- chart base ---- */

    function Chart(container) {
        this.container = container;
        this._listeners = {};
        this._selection = [];
        this._marks = [];
        this._onClick = this._onClick.bind(this);
    }

    Chart.prototype.getSelection = function () {
        return this._selection.map(function (s) {
            var copy = {};
            if (s.row !== undefined) {
                copy.row = s.row;
            }
            if (s.column !== undefined) {
                copy.column = s.column;
            }
            return copy;
        });
    };

    // A click on a mark (bar, slice, legend entry) toggles it as the single selection and fires "select".
    Chart.prototype._bindMark = function (node, sel) {
        var chart = this;
        node.style.cursor = 'pointer';
        node.addEventListener('click', function (e) {
            var was = chart._selection.length === 1 && chart._selection[0].row === sel.row &&
                chart._selection[0].column === sel.column;
            chart._selection = was ? [] : [sel];
            chart._paintSelection();
            events.trigger(chart, 'select');
            e.stopPropagation();
            events.trigger(chart, 'click');
        });
        this._marks.push({node: node, sel: sel});
    };

    Chart.prototype._paintSelection = function () {
        var chosen = this._selection[0];
        this._marks.forEach(function (m) {
            var on = chosen && chosen.row === m.sel.row && chosen.column === m.sel.column;
            m.node.setAttribute('stroke', on ? SELECT_COLOR : 'none');
            m.node.setAttribute('stroke-width', on ? 2 : 0);
        });
    };

    Chart.prototype._onClick = function () {
        events.trigger(this, 'click');
    };

    Chart.prototype._start = function (options) {
        var c = this.container;
        var w = options.width > 0 ? options.width : c.clientWidth;
        var h = options.height > 0 ? options.height : c.clientHeight;
        this.opts = options || {};
        this.fontSize = this.opts.fontSize || 11;
        this.fontName = this.opts.fontName || 'sans-serif';
        this.colors = this.opts.colors && this.opts.colors.length ? this.opts.colors : DEFAULT_COLORS;
        this.w = w > 100 ? w : 400;
        // a little shorter than the box, so that the bottom axis title is not at its very edge
        this.h = h > 100 ? h - 14 : 300;
        this._selection = [];
        this._marks = [];
        while (c.firstChild) {
            c.removeChild(c.firstChild);
        }
        this.svg = svgEl(c, 'svg', {
            width: this.w, height: this.h, viewBox: '0 0 ' + this.w + ' ' + this.h, role: 'img',
            'font-family': this.fontName, 'font-size': this.fontSize, fill: TEXT_COLOR
        });
        this.svg.style.display = 'block';
        this.svg.addEventListener('click', this._onClick);
    };

    // One row of legend entries across the top; clicking an entry selects the series (column).
    // Returns the height used.
    Chart.prototype._legend = function (items, selectable) {
        var x = 10;
        var y = 12;
        var fs = this.fontSize;
        var chart = this;
        items.forEach(function (it) {
            var width = 16 + textWidth(it.label, fs) + 14;
            var g = svgEl(chart.svg, 'g', {});
            var swatch;
            if (x + width > chart.w - 10 && x > 10) {
                x = 10;
                y += fs + 8;
            }
            swatch = svgEl(g, 'rect', {x: x, y: y - fs + 1, width: 10, height: 10, fill: it.color});
            svgEl(g, 'text', {x: x + 15, y: y}, shorten(it.label, 40));
            tooltip(g, it.label);
            if (selectable && it.column !== undefined) {
                // the whole entry is the click target; selection outline goes on the swatch
                chart._bindMark(swatch, {column: it.column});
                g.style.cursor = 'pointer';
                g.addEventListener('click', function () {
                    swatch.dispatchEvent(new MouseEvent('click', {bubbles: false}));
                });
            }
            x += width;
        });
        return y + 6;
    };

    /* ---- column and bar charts ---- */

    function CartesianChart(container, horizontal) {
        Chart.call(this, container);
        this.horizontal = horizontal;
    }
    CartesianChart.prototype = Object.create(Chart.prototype);

    CartesianChart.prototype.draw = function (data, options) {
        var horizontal = this.horizontal;
        var rows = data.getNumberOfRows();
        var cols = data.getNumberOfColumns() - 1;
        var fs, i, j, max = 0, scale, top, left, right = 16, bottom, pw, ph, labels = [], maxLabel = 0;
        var legendItems = [], series = [], hAxisTitle, vAxisTitle, titleCat, titleVal, catLen, bandSize;
        var rotate, svg, tick, v, p, x, y, bar, slot, bw, nTicks;

        this._start(options || {});
        svg = this.svg;
        fs = this.fontSize;
        for (j = 1; j <= cols; j++) {
            series.push({column: j, label: data.getColumnLabel(j), color: this.colors[(j - 1) % this.colors.length]});
        }
        for (i = 0; i < rows; i++) {
            labels.push(String(data.getValue(i, 0)));
            maxLabel = Math.max(maxLabel, textWidth(shorten(labels[i], 24), fs));
            for (j = 1; j <= cols; j++) {
                max = Math.max(max, num(data.getValue(i, j)));
            }
        }
        scale = niceScale(max);
        hAxisTitle = (this.opts.hAxis || {}).title;
        vAxisTitle = (this.opts.vAxis || {}).title;
        titleCat = horizontal ? vAxisTitle : hAxisTitle;
        titleVal = horizontal ? hAxisTitle : vAxisTitle;

        top = cols > 1 ? this._legend(series, true) + 8 : ((this.opts.chartArea || {}).top || 30) / 2;
        right = 16;
        if (horizontal) {
            left = (titleCat ? 22 : 8) + maxLabel + 8;
            bottom = fs + 12 + (titleVal ? 20 : 0);
        } else {
            left = (titleVal ? 22 : 8) + textWidth(fmt(scale.max), fs) + 8;
            // category labels are turned when they do not fit side by side
            rotate = (this.w - left - right) / Math.max(rows, 1) < maxLabel + 6;
            bottom = (rotate ? maxLabel * 0.75 + 8 : fs + 8) + (titleCat ? 22 : 4);
        }
        pw = Math.max(this.w - left - right, 50);
        ph = Math.max(this.h - top - bottom, 50);

        // value axis: gridlines and tick labels
        nTicks = Math.round(scale.max / scale.step);
        for (i = 0; i <= nTicks; i++) {
            v = i * scale.step;
            p = v / scale.max;
            if (horizontal) {
                x = left + p * pw;
                svgEl(svg, 'line', {x1: x, y1: top, x2: x, y2: top + ph, stroke: i === 0 ? AXIS_COLOR : GRID_COLOR});
                svgEl(svg, 'text', {x: x, y: top + ph + fs + 4, 'text-anchor': 'middle'}, fmt(v));
            } else {
                y = top + ph - p * ph;
                svgEl(svg, 'line', {x1: left, y1: y, x2: left + pw, y2: y, stroke: i === 0 ? AXIS_COLOR : GRID_COLOR});
                svgEl(svg, 'text', {x: left - 6, y: y + fs / 3, 'text-anchor': 'end'}, fmt(v));
            }
        }

        // bars, one slot per category and a bar per series in it
        catLen = horizontal ? ph : pw;
        bandSize = catLen / Math.max(rows, 1);
        slot = bandSize * 0.8;
        bw = slot / Math.max(cols, 1);
        for (i = 0; i < rows; i++) {
            for (j = 1; j <= cols; j++) {
                v = num(data.getValue(i, j));
                p = v / scale.max;
                if (horizontal) {
                    bar = svgEl(svg, 'rect', {
                        x: left, y: top + i * bandSize + bandSize * 0.1 + (j - 1) * bw,
                        width: p * pw, height: bw, fill: series[j - 1].color
                    });
                } else {
                    bar = svgEl(svg, 'rect', {
                        x: left + i * bandSize + bandSize * 0.1 + (j - 1) * bw, y: top + ph - p * ph,
                        width: bw, height: p * ph, fill: series[j - 1].color
                    });
                }
                tooltip(bar, labels[i] + (cols > 1 ? ', ' + series[j - 1].label : '') + ': ' + fmt(v));
                this._bindMark(bar, {row: i, column: j});
            }
            // category label
            if (horizontal) {
                svgEl(svg, 'text', {x: left - 6, y: top + i * bandSize + bandSize / 2 + fs / 3, 'text-anchor': 'end'},
                    shorten(labels[i], 24));
            } else {
                x = left + i * bandSize + bandSize / 2;
                y = top + ph + fs + 4;
                if (rotate) {
                    svgEl(svg, 'text', {x: x, y: y - 2, 'text-anchor': 'end', transform: 'rotate(-45 ' + x + ' ' + (y - 2) + ')'},
                        shorten(labels[i], 24));
                } else {
                    svgEl(svg, 'text', {x: x, y: y, 'text-anchor': 'middle'}, shorten(labels[i], 24));
                }
            }
        }

        // axis titles
        if (titleCat) {
            if (horizontal) {
                svgEl(svg, 'text', {x: 12, y: top + ph / 2, 'text-anchor': 'middle', transform: 'rotate(-90 12 ' + (top + ph / 2) + ')'}, titleCat);
            } else {
                svgEl(svg, 'text', {x: left + pw / 2, y: this.h - 6, 'text-anchor': 'middle'}, titleCat);
            }
        }
        if (titleVal) {
            if (horizontal) {
                svgEl(svg, 'text', {x: left + pw / 2, y: this.h - 6, 'text-anchor': 'middle'}, titleVal);
            } else {
                svgEl(svg, 'text', {x: 12, y: top + ph / 2, 'text-anchor': 'middle', transform: 'rotate(-90 12 ' + (top + ph / 2) + ')'}, titleVal);
            }
        }
    };

    /* ---- pie chart ---- */

    function PieChart(container) {
        Chart.call(this, container);
    }
    PieChart.prototype = Object.create(Chart.prototype);

    PieChart.prototype.draw = function (data, options) {
        var rows = data.getNumberOfRows();
        var total = 0, i, v, top, cx, cy, r, angle = -Math.PI / 2, a2, items = [], slice, mid, large, d, color, pct;
        var self = this;

        this._start(options || {});
        for (i = 0; i < rows; i++) {
            total += Math.max(num(data.getValue(i, 1)), 0);
            items.push({label: String(data.getValue(i, 0)), color: this.colors[i % this.colors.length]});
        }
        top = this._legend(items, false) + 4;
        cx = this.w / 2;
        cy = top + (this.h - top) / 2;
        r = Math.max(Math.min(this.w / 2, (this.h - top) / 2) - 12, 20);
        if (!(total > 0)) {
            return;
        }
        for (i = 0; i < rows; i++) {
            v = Math.max(num(data.getValue(i, 1)), 0);
            if (v === 0) {
                continue;
            }
            pct = v / total;
            a2 = angle + pct * 2 * Math.PI;
            color = items[i].color;
            if (pct > 0.9999) {
                slice = svgEl(this.svg, 'circle', {cx: cx, cy: cy, r: r, fill: color});
            } else {
                large = pct > 0.5 ? 1 : 0;
                d = 'M' + cx + ',' + cy + ' L' + (cx + r * Math.cos(angle)) + ',' + (cy + r * Math.sin(angle)) +
                    ' A' + r + ',' + r + ' 0 ' + large + ' 1 ' + (cx + r * Math.cos(a2)) + ',' + (cy + r * Math.sin(a2)) + ' Z';
                slice = svgEl(this.svg, 'path', {d: d, fill: color, stroke: '#fff', 'stroke-width': 1});
            }
            tooltip(slice, items[i].label + ': ' + fmt(v) + ' (' + Math.round(pct * 1000) / 10 + '%)');
            self._bindMark(slice, {row: i});
            if (pct >= 0.05) {
                mid = angle + (a2 - angle) / 2;
                svgEl(this.svg, 'text', {
                    x: cx + r * 0.65 * Math.cos(mid), y: cy + r * 0.65 * Math.sin(mid) + this.fontSize / 3,
                    'text-anchor': 'middle', fill: '#000', 'pointer-events': 'none'
                }, Math.round(pct * 100) + '%');
            }
            angle = a2;
        }
    };

    /* ---- the global ---- */

    function later(fn) {
        if (typeof fn === 'function') {
            setTimeout(fn, 0);
        }
    }

    var google = root.google = root.google || {};
    var visualization = google.visualization = google.visualization || {};

    visualization.DataTable = DataTable;
    visualization.arrayToDataTable = function (rows) {
        return new DataTable(rows);
    };
    visualization.events = events;
    visualization.ColumnChart = function (container) {
        return new CartesianChart(container, false);
    };
    visualization.BarChart = function (container) {
        return new CartesianChart(container, true);
    };
    visualization.PieChart = PieChart;

    // The original loader fetches the chart packages and then calls the callback; everything is
    // already here, so only the asynchronous call is kept.
    google.load = function (name, version, settings) {
        later(settings && settings.callback);
    };
    google.setOnLoadCallback = later;
    google.charts = {
        load: function () {},
        setOnLoadCallback: later
    };
}(window));
