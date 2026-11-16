package org.intermine.task;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.CLOBVAL_COLUMN;
import static org.intermine.objectstore.intermine.ObjectStoreInterMineImpl.CLOB_TABLE_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.intermine.DdlGenerator;
import org.intermine.objectstore.intermine.ObjectStoreInterMineImpl;
import org.intermine.sql.Database;
import org.intermine.sql.DatabaseFactory;
import org.intermine.sql.DatabaseUtil;
import org.intermine.util.PropertiesUtil;

/**
 * Creates the tables of an objectstore database: empties the database, then executes the DDL
 * that DdlGenerator makes from the objectstore's model and properties (os.NAME.model,
 * truncatedClasses, noNotXml, missingTables), and creates the sequences.
 *
 * @author Mark Woodbridge
 */
public class BuildDbTask extends Task
{
    private static final Logger LOG = Logger.getLogger(BuildDbTask.class);
    private static final String SERIAL_SEQUENCE_NAME = "serial";
    private File tempDir;
    protected Database database;
    private String databaseAlias;
    private String os;
    private String model;

    /**
     * Sets the objectstore
     * @param os String used to identify objectstore and therefore database
     */
    public void setOsName(String os) {
        this.os = os;
        try {
            this.databaseAlias = PropertiesUtil.getProperties().getProperty(os + ".db");
            this.database = DatabaseFactory.getDatabase(databaseAlias);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the model
     * @param model String used to identify the model (useprofrile, genomic...)
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Sets a directory to write the generated SQL to (OSNAME-schema.sql), for inspection.
     * Optional: the SQL is executed whether or not it is written.
     * @param tempDir the directory location
     */
    public void setTempdir(File tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Ignored. Up to InterMine 5.1 this named the Torque schema XML (OSNAME-schema.xml) made by
     * the torque Ant task; the DDL is now generated from the model and the objectstore
     * properties. Kept so that build files that still pass the attribute work.
     * @param schemafile ignored
     * @deprecated the DDL is generated from the objectstore properties
     */
    @Deprecated
    public void setSchemafile(String schemafile) {
        LOG.warn("The schemafile attribute of BuildDbTask is ignored (no Torque schema any more)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() {
        if (databaseAlias == null) {
            throw new BuildException("Could not find database attribute for: '" + os + ".db'");
        }
        if (database == null) {
            throw new BuildException("Could not access database: " + databaseAlias);
        }
        try {
            List<String> statements;
            try {
                DdlGenerator generator = new DdlGenerator(DdlGenerator.getDatabaseSchema(os));
                statements = generator.getStatements();
                if (tempDir != null) {
                    FileUtils.writeStringToFile(new File(tempDir, os + "-schema.sql"),
                            generator.getSql(), StandardCharsets.UTF_8);
                }
            } catch (ObjectStoreException | IOException e) {
                throw new BuildException("Cannot generate the tables of " + os + ": "
                        + e.getMessage(), e);
            }

            Connection c = null;
            try {
                c = database.getConnection();
                c.setAutoCommit(true);
                try {
                    DatabaseUtil.removeAllTables(c);
                    DatabaseUtil.removeSequence(c, SERIAL_SEQUENCE_NAME);
                    DatabaseUtil.removeSequence(
                        c, ObjectStoreInterMineImpl.UNIQUE_INTEGER_SEQUENCE_NAME);
                } catch (SQLException e) {
                    LOG.warn("Failed to remove all tables from database: " + e);
                }
                for (String statement : statements) {
                    try (Statement s = c.createStatement()) {
                        s.execute(statement);
                    } catch (SQLException e) {
                        throw new BuildException(e.getMessage() + " - for database: "
                                + databaseAlias + ", in: " + statement, e);
                    }
                }
                LOG.info("Created " + statements.size() + " tables and indexes in "
                        + databaseAlias);
                execute(c, "CREATE SEQUENCE " + SERIAL_SEQUENCE_NAME);
                execute(c, "CREATE SEQUENCE "
                        + ObjectStoreInterMineImpl.UNIQUE_INTEGER_SEQUENCE_NAME);
                execute(c, "ALTER TABLE " + CLOB_TABLE_NAME + " ALTER COLUMN " + CLOBVAL_COLUMN
                        + " SET STORAGE PLAIN");
                //create bagvalues table in userprofile
                if (model != null && model.contains("userprofile")) {
                    try {
                        DatabaseUtil.createBagValuesTables(c);
                    } catch (SQLException e) {
                        LOG.info("Failed to create bagvalues table: " + e);
                    }
                }
            } catch (SQLException e) {
                throw new BuildException("Cannot connect to database " + databaseAlias, e);
            } finally {
                if (c != null) {
                    try {
                        c.close();
                    } catch (SQLException e) {
                        LOG.error("Failed to close connection", e);
                    }
                }
            }
        } finally {
            database.shutdown();
        }
    }

    /** Executes a statement whose failure (for example an existing sequence) is only logged. */
    private static void execute(Connection c, String sql) {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            LOG.info("Failed to execute " + sql + ": " + e);
        }
    }
}
