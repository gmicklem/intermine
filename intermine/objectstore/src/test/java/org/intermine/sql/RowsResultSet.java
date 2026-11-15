package org.intermine.sql;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

/**
 * A read-only, forward-only java.sql.ResultSet over an in-memory table, for tests that need to hand
 * a ResultSet to the code under test without a database. Only the getters the InterMine code uses
 * are supported (get* by column index or label, next, wasNull, findColumn, getMetaData, close);
 * any other ResultSet method throws UnsupportedOperationException.
 *
 * @author InterMine
 */
public final class RowsResultSet
{
    private RowsResultSet() {
        // utility class
    }

    /**
     * Create a ResultSet whose column types are taken from the first non-null value in each column.
     * @param columnNames the column names, which are also the column labels
     * @param rows the rows; each has one element per column, and null is SQL NULL
     * @return the ResultSet, positioned before the first row
     */
    public static ResultSet create(String[] columnNames, Object[][] rows) {
        int[] types = new int[columnNames.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = Types.NULL;
            for (Object[] row : rows) {
                if (row[i] != null) {
                    types[i] = typeOf(row[i]);
                    break;
                }
            }
        }
        return create(columnNames, types, rows);
    }

    /**
     * Create a ResultSet with explicit column types.
     * @param columnNames the column names, which are also the column labels
     * @param columnTypes the java.sql.Types constants of the columns
     * @param rows the rows; each has one element per column, and null is SQL NULL
     * @return the ResultSet, positioned before the first row
     */
    public static ResultSet create(String[] columnNames, int[] columnTypes, Object[][] rows) {
        for (Object[] row : rows) {
            if (row.length != columnNames.length) {
                throw new IllegalArgumentException("a row has " + row.length + " values for "
                        + columnNames.length + " columns");
            }
        }
        return (ResultSet) Proxy.newProxyInstance(RowsResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class}, new Cursor(columnNames, columnTypes, rows));
    }

    private static int typeOf(Object o) {
        if (o instanceof String) {
            return Types.VARCHAR;
        } else if (o instanceof Integer) {
            return Types.INTEGER;
        } else if (o instanceof Long) {
            return Types.BIGINT;
        } else if (o instanceof Boolean) {
            return Types.BOOLEAN;
        } else if (o instanceof Double) {
            return Types.DOUBLE;
        }
        return Types.OTHER;
    }

    private static class Cursor implements InvocationHandler
    {
        private final String[] names;
        private final int[] types;
        private final Object[][] rows;
        private int row = -1;
        private boolean wasNull;
        private final ResultSetMetaData metaData;

        Cursor(String[] names, int[] types, Object[][] rows) {
            this.names = names.clone();
            this.types = types.clone();
            this.rows = rows;
            this.metaData = (ResultSetMetaData) Proxy.newProxyInstance(
                    RowsResultSet.class.getClassLoader(), new Class<?>[] {ResultSetMetaData.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method m, Object[] args) {
                            return metaData(m, args);
                        }
                    });
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws SQLException {
            String name = m.getName();
            if ("next".equals(name)) {
                row++;
                return row < rows.length;
            } else if ("close".equals(name)) {
                return null;
            } else if ("wasNull".equals(name)) {
                return wasNull;
            } else if ("getMetaData".equals(name)) {
                return metaData;
            } else if ("findColumn".equals(name)) {
                return indexOf((String) args[0]);
            } else if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(this);
            } else if ("toString".equals(name)) {
                return "RowsResultSet" + Arrays.toString(names) + "[" + rows.length + " rows]";
            } else if (name.startsWith("get") && args != null && args.length == 1
                    && (args[0] instanceof Integer || args[0] instanceof String)) {
                int col = args[0] instanceof Integer ? (Integer) args[0] : indexOf((String) args[0]);
                if (row < 0 || row >= rows.length) {
                    throw new SQLException("not on a row");
                }
                if (col < 1 || col > names.length) {
                    throw new SQLException("no column " + col);
                }
                Object value = rows[row][col - 1];
                wasNull = value == null;
                return convert(value, m.getReturnType());
            }
            throw new UnsupportedOperationException(name);
        }

        private int indexOf(String label) throws SQLException {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equalsIgnoreCase(label)) {
                    return i + 1;
                }
            }
            throw new SQLException("no column " + label);
        }

        private Object convert(Object v, Class<?> type) {
            if (type == Object.class) {
                return v;
            } else if (type == String.class) {
                return v == null ? null : v.toString();
            } else if (type == boolean.class) {
                return v != null && (v instanceof Boolean ? (Boolean) v
                        : Boolean.parseBoolean(v.toString()));
            } else if (type == int.class) {
                return v == null ? 0 : (v instanceof Number ? ((Number) v).intValue()
                        : Integer.parseInt(v.toString()));
            } else if (type == long.class) {
                return v == null ? 0L : (v instanceof Number ? ((Number) v).longValue()
                        : Long.parseLong(v.toString()));
            } else if (type == double.class) {
                return v == null ? 0d : (v instanceof Number ? ((Number) v).doubleValue()
                        : Double.parseDouble(v.toString()));
            } else if (type == BigDecimal.class) {
                return v == null ? null : new BigDecimal(v.toString());
            }
            throw new UnsupportedOperationException("getter returning " + type.getName());
        }

        private Object metaData(Method m, Object[] args) {
            String name = m.getName();
            if ("getColumnCount".equals(name)) {
                return names.length;
            } else if ("getColumnName".equals(name) || "getColumnLabel".equals(name)) {
                return names[(Integer) args[0] - 1];
            } else if ("getColumnType".equals(name)) {
                return types[(Integer) args[0] - 1];
            } else if ("equals".equals(name)) {
                return args[0] == metaData;
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(this);
            }
            throw new UnsupportedOperationException(name);
        }
    }
}
