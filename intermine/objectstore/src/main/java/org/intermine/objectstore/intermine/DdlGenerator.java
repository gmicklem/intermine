package org.intermine.objectstore.intermine;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.BAGID_COLUMN;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.BAGVAL_COLUMN;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.CLOBID_COLUMN;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.CLOBPAGE_COLUMN;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.CLOBVAL_COLUMN;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.CLOB_TABLE_NAME;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.INT_BAG_TABLE_NAME;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.intermine.metadata.AttributeDescriptor;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.model.InterMineObject;
import org.intermine.modelproduction.MetadataManager;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.sql.DatabaseUtil;
import org.intermine.util.PropertiesUtil;

/**
 * Generates the PostgreSQL DDL (CREATE TABLE and CREATE INDEX statements) of an InterMine
 * objectstore database from its DatabaseSchema: one table per table-mastering class, one
 * indirection table per many-to-many collection, and the intermine_metadata, osbag_int and clob
 * tables. Up to InterMine 5.1 the same SQL was made in two steps by vendored Apache Torque 3.2
 * jars (an XML schema, then velocity templates); the statements, column order, types and
 * constraint names are the ones Torque's PostgreSQL templates produced, without the DROP TABLE
 * statements (BuildDbTask empties the database first).
 *
 * @author Andrew Varley
 * @author Matthew Wakeling
 */
public class DdlGenerator
{
    /** The version number of the database format */
    public static final int FORMAT_VERSION = 1;

    private static final String INDENT = "    ";

    private final DatabaseSchema schema;

    /**
     * Constructor.
     *
     * @param schema the DatabaseSchema to generate the DDL of
     */
    public DdlGenerator(DatabaseSchema schema) {
        this.schema = schema;
    }

    /**
     * Creates the DatabaseSchema of an objectstore from its properties in intermine.properties
     * (os.NAME.model, truncatedClasses, noNotXml and missingTables).
     *
     * @param osName the objectstore name, for example "os.production"
     * @return the DatabaseSchema
     * @throws ObjectStoreException if a property is invalid
     */
    public static DatabaseSchema getDatabaseSchema(String osName) throws ObjectStoreException {
        Properties props = PropertiesUtil.stripStart(osName,
                PropertiesUtil.getPropertiesStartingWith(osName));
        String modelName = props.getProperty("model");
        if (modelName == null) {
            throw new ObjectStoreException("No model given for objectstore " + osName
                    + " (property " + osName + ".model)");
        }
        Model model = Model.getInstanceByName(modelName);
        if (model == null) {
            throw new ObjectStoreException("Cannot load the model " + modelName
                    + " of objectstore " + osName);
        }
        List<ClassDescriptor> truncated = new ArrayList<ClassDescriptor>();
        String truncatedClasses = props.getProperty("truncatedClasses");
        if (truncatedClasses != null) {
            for (String className : truncatedClasses.split(",")) {
                ClassDescriptor cld = model.getClassDescriptorByName(className);
                if (cld == null) {
                    throw new ObjectStoreException("Truncated class " + className
                            + " does not exist in the model");
                }
                truncated.add(cld);
            }
        }
        String noNotXmlString = props.getProperty("noNotXml");
        boolean noNotXml;
        if (noNotXmlString == null || "true".equals(noNotXmlString)) {
            noNotXml = true;
        } else if ("false".equals(noNotXmlString)) {
            noNotXml = false;
        } else {
            throw new ObjectStoreException("Invalid value for property noNotXml: "
                    + noNotXmlString);
        }
        Set<String> missingTables = new HashSet<String>();
        String missingTablesString = props.getProperty("missingTables");
        if (missingTablesString != null) {
            for (String table : missingTablesString.split(",")) {
                missingTables.add(table.toLowerCase());
            }
        }
        return new DatabaseSchema(model, truncated, noNotXml, missingTables, FORMAT_VERSION);
    }

    /**
     * Returns the DDL statements, without a terminating semicolon, in the order to execute them.
     *
     * @return a List of SQL statements
     * @throws ObjectStoreException if the schema is invalid
     */
    public List<String> getStatements() throws ObjectStoreException {
        List<Table> tables = new ArrayList<Table>();
        // the indirection table of a many-to-many collection is made once, for the first side seen
        Set<CollectionDescriptor> indirections = new LinkedHashSet<CollectionDescriptor>();
        for (ClassDescriptor cld : schema.getModel().getClassDescriptors()) {
            if (cld == schema.getTableMaster(cld)) {
                String tableName = DatabaseUtil.getTableName(cld);
                if (!schema.getMissingTables().contains(tableName.toLowerCase())) {
                    tables.add(classTable(cld, tableName));
                }
            }
            for (CollectionDescriptor collection : cld.getCollectionDescriptors()) {
                if (FieldDescriptor.M_N_RELATION == collection.relationType()
                        && !indirections.contains(collection.getReverseReferenceDescriptor())) {
                    indirections.add(collection);
                }
            }
        }
        for (CollectionDescriptor collection : indirections) {
            Table table = new Table(DatabaseUtil.getIndirectionTableName(collection));
            table.column(DatabaseUtil.getInwardIndirectionColumnName(collection,
                    schema.getVersion()), "int");
            table.column(DatabaseUtil.getOutwardIndirectionColumnName(collection,
                    schema.getVersion()), "int");
            tables.add(table);
        }

        Table metadata = new Table(MetadataManager.METADATA_TABLE);
        metadata.column("key", "java.lang.String");
        metadata.column("value", "java.lang.String");
        metadata.columns.add("blob_value BYTEA");
        metadata.unique(MetadataManager.METADATA_TABLE + "_key", "key");
        tables.add(metadata);

        Table intBag = new Table(INT_BAG_TABLE_NAME);
        intBag.column(BAGID_COLUMN, "java.lang.Integer");
        intBag.column(BAGVAL_COLUMN, "java.lang.Integer");
        intBag.unique(INT_BAG_TABLE_NAME + "_index1", BAGID_COLUMN + ", " + BAGVAL_COLUMN);
        intBag.indexes.add("CREATE INDEX " + INT_BAG_TABLE_NAME + "_index2 ON "
                + INT_BAG_TABLE_NAME + " (" + BAGVAL_COLUMN + ", " + BAGID_COLUMN + ")");
        tables.add(intBag);

        Table clob = new Table(CLOB_TABLE_NAME);
        clob.column(CLOBID_COLUMN, "java.lang.Integer");
        clob.column(CLOBPAGE_COLUMN, "java.lang.Integer");
        clob.column(CLOBVAL_COLUMN, "java.lang.String");
        clob.unique(CLOB_TABLE_NAME + "_index", CLOBID_COLUMN + ", " + CLOBPAGE_COLUMN);
        tables.add(clob);

        List<String> statements = new ArrayList<String>();
        for (Table table : tables) {
            statements.add(table.createStatement());
            statements.addAll(table.indexes);
        }
        return statements;
    }

    /**
     * Returns the DDL as one SQL script, each statement terminated by a semicolon.
     *
     * @return the SQL
     * @throws ObjectStoreException if the schema is invalid
     */
    public String getSql() throws ObjectStoreException {
        StringBuilder sb = new StringBuilder();
        for (String statement : getStatements()) {
            sb.append(statement).append(";\n\n");
        }
        return sb.toString();
    }

    private Table classTable(ClassDescriptor cld, String tableName) throws ObjectStoreException {
        Table table = new Table(tableName);
        if (!(schema.isMissingNotXml() || schema.isFlatMode(cld.getType()))
                || InterMineObject.class.equals(cld.getType())) {
            table.column("OBJECT", "java.lang.String");
        }
        DatabaseSchema.Fields fields = schema.getTableFields(cld);
        for (AttributeDescriptor field : fields.getAttributes()) {
            table.column(DatabaseUtil.getColumnName(field), field.getType());
        }
        for (ReferenceDescriptor field : fields.getReferences()) {
            table.column(DatabaseUtil.getColumnName(field), "java.lang.Integer");
        }
        if (cld.getFieldDescriptorByName("id") != null) {
            if (schema.isTruncated(cld)) {
                table.column("class", "java.lang.String");
                table.column("tableclass", "java.lang.String");
                table.unique(tableName + "_pkey", "id, tableclass");
            } else {
                if (!schema.isFlatMode(cld.getType())) {
                    table.column("class", "java.lang.String");
                }
                table.unique(tableName + "_pkey", "id");
            }
        }
        return table;
    }

    /**
     * Returns the PostgreSQL type of a column (or of a cast) for an attribute type of the model.
     *
     * @param type a primitive type name ("int"), or the name of its wrapper class,
     * java.lang.String, java.util.Date, java.math.BigDecimal or
     * org.intermine.objectstore.query.ClobAccess
     * @return the SQL type, for example INTEGER
     * @throws IllegalArgumentException if the type cannot be stored in a column
     */
    public static String getSqlType(String type) {
        switch (type) {
            case "short":
            case "java.lang.Short":
                return "INT2";
            case "int":
            case "java.lang.Integer":
                return "INTEGER";
            case "long":
            case "java.lang.Long":
            case "java.util.Date":
                return "INT8";
            case "java.lang.String":
            case "org.intermine.objectstore.query.ClobAccess":
                return "TEXT";
            case "boolean":
            case "java.lang.Boolean":
                return "BOOLEAN";
            case "float":
            case "java.lang.Float":
                return "REAL";
            case "double":
            case "java.lang.Double":
                return "DOUBLE PRECISION";
            case "java.math.BigDecimal":
                return "NUMERIC";
            default:
                throw new IllegalArgumentException("Invalid type \"" + type + "\"");
        }
    }

    /** A table to create: column and constraint lines, and the indexes to create after it. */
    private static final class Table
    {
        private final String name;
        private final List<String> columns = new ArrayList<String>();
        private final List<String> constraints = new ArrayList<String>();
        private final List<String> indexes = new ArrayList<String>();

        Table(String name) {
            this.name = name;
        }

        /** A column of a model type: primitives and the id are NOT NULL, as Torque made them. */
        void column(String columnName, String type) {
            boolean required = type.indexOf('.') == -1 || "id".equals(columnName);
            columns.add(columnName + " " + getSqlType(type) + (required ? " NOT NULL" : ""));
        }

        void unique(String constraintName, String columnList) {
            constraints.add("CONSTRAINT " + constraintName + " UNIQUE (" + columnList + ")");
        }

        String createStatement() {
            List<String> lines = new ArrayList<String>(columns);
            lines.addAll(constraints);
            StringBuilder sb = new StringBuilder("CREATE TABLE " + name + "\n(\n");
            String separator = "";
            for (String line : lines) {
                sb.append(separator).append(INDENT).append(line);
                separator = ",\n";
            }
            return sb.append("\n)").toString();
        }
    }
}
