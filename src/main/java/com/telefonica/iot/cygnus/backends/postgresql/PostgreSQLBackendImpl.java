/**
 * Copyright 2015 Telefonica Investigación y Desarrollo, S.A.U
 *
 * This file is part of fiware-cygnus (FI-WARE project).
 *
 * fiware-cygnus is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * fiware-cygnus is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with fiware-cygnus. If not, see
 * http://www.gnu.org/licenses/.
 *
 * For those usages not covered by the GNU Affero General Public License please contact with iot_support at tid dot es
 */

package com.telefonica.iot.cygnus.backends.postgresql;

import com.telefonica.iot.cygnus.errors.CygnusBadContextData;
import com.telefonica.iot.cygnus.errors.CygnusPersistenceError;
import com.telefonica.iot.cygnus.errors.CygnusRuntimeError;
import com.telefonica.iot.cygnus.log.CygnusLogger;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @author hermanjunge
 *
 * PostgreSQL related operations (database and table creation, context data insertion) when dealing with a PostgreSQL
 * persistence backend.
 */
public class PostgreSQLBackendImpl implements PostgreSQLBackend {

    private static final String DRIVER_NAME = "org.postgresql.Driver";
    private PostgreSQLDriver driver;
    private static final CygnusLogger LOGGER = new CygnusLogger(PostgreSQLBackendImpl.class);

    /**
     * Constructor.
     * @param postgresqlHost
     * @param postgresqlPort
     * @param postgresqlUsername
     * @param postgresqlPassword
     */
    public PostgreSQLBackendImpl(String postgresqlHost, String postgresqlPort, String postgresqlUsername, String postgresqlPassword) {
        driver = new PostgreSQLDriver(postgresqlHost, postgresqlPort, postgresqlUsername, postgresqlPassword);
    } // PostgreSQLBackendImpl

    /**
     * Sets the PostgreSQLDriver driver. It is protected since it is only used by the tests.
     * @param driver The PostgreSQLDriver driver to be set.
     */
    protected void setDriver(PostgreSQLDriver driver) {
        this.driver = driver;
    } // setDriver

    protected PostgreSQLDriver getDriver() {
        return driver;
    } // getDriver

    /**
     * Creates a schema given its name, if not exists.
     * @param schemaName
     * @throws Exception
     */
    @Override
    public void createSchema(String schemaName) throws Exception {
        Statement stmt = null;

        // get a connection to an empty database
        Connection con = driver.getConnection("");

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
            LOGGER.debug("Executing SQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        closePostgreSQLObjects(con, stmt);
    } // createSchema

    /**
     * Creates a table, given its name, if not exists in the given schema
     * @param schemaName
     * @param tableName
     * @throws Exception
     */
    @Override
    public void createTable(String schemaName, String tableName, String typedFieldNames) throws Exception {
        Statement stmt = null;

        // get a connection to the given schema
        Connection con = driver.getConnection(schemaName);

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "CREATE TABLE IF NOT EXISTS " + tableName + " " + typedFieldNames;
            LOGGER.debug("Executing SQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        closePostgreSQLObjects(con, stmt);
    } // createTable

    @Override
    public void insertContextData(String schemaName, String tableName, String fieldNames, String fieldValues)
        throws Exception {
        Statement stmt = null;

        // get a connection to the given database
        Connection con = driver.getConnection(schemaName);

        try {
            stmt = con.createStatement();
        } catch (Exception e) {
            throw new CygnusRuntimeError(e.getMessage());
        } // try catch

        try {
            String query = "INSERT INTO " + tableName + " " + fieldNames + " VALUES " + fieldValues;
            LOGGER.debug("Executing SQL query '" + query + "'");
            stmt.executeUpdate(query);
        } catch (SQLTimeoutException e) {
            throw new CygnusPersistenceError(e.getMessage());
        } catch (SQLException e) {
            throw new CygnusBadContextData(e.getMessage());
        } // try catch
    } // insertContextData

    /**
     * Close all the PostgreSQL objects previously opened by createSchema and createTable.
     * @param con
     * @param stmt
     * @return True if the PostgreSQL objects have been closed, false otherwise.
     */
    private void closePostgreSQLObjects(Connection con, Statement stmt) throws Exception {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                throw new CygnusRuntimeError("The Hive connection could not be closed. Details="
                        + e.getMessage());
            } // try catch
        } // if

        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                throw new CygnusRuntimeError("The Hive statement could not be closed. Details="
                        + e.getMessage());
            } // try catch
        } // if
    } // closePostgreSQLObjects

    protected class PostgreSQLDriver {

        private final HashMap<String, Connection> connections;
        private final String postgresqlHost;
        private final String postgresqlPort;
        private final String postgresqlUsername;
        private final String postgresqlPassword;

        /**
         * Constructor
         * @param postgresqlHost
         * @param postgresqlPort
         * @param postgresqlUsername
         * @param postgresqlPassword
         */
        public PostgreSQLDriver(String postgresqlHost, String postgresqlPort, String postgresqlUsername, String postgresqlPassword) {
            connections = new HashMap<String, Connection>();
            this.postgresqlHost = postgresqlHost;
            this.postgresqlPort = postgresqlPort;
            this.postgresqlUsername = postgresqlUsername;
            this.postgresqlPassword = postgresqlPassword;
        } // PostgreSQLDriver

        /**
         * Gets a connection to the PostgreSQL server.
         * @param schemaName
         * @return
         * @throws Exception
         */
        public Connection getConnection(String schemaName) throws Exception {
            try {
                // FIXME: the number of cached connections should be limited to a certain number; with such a limit
                //        number, if a new connection is needed, the oldest one is closed
                Connection con = connections.get(schemaName);

                if (con == null || !con.isValid(0)) {
                    if (con != null) {
                        con.close();
                    } // if

                    con = createConnection(schemaName);
                    connections.put(schemaName, con);
                } // if

                return con;
            } catch (ClassNotFoundException e) {
                throw new CygnusPersistenceError(e.getMessage());
            } catch (SQLException e) {
                throw new CygnusPersistenceError(e.getMessage());
            } // try catch
        } // getConnection

        /**
         * Gets if a connection is created for the given schema. It is protected since it is only used in the tests.
         * @param schemaName
         * @return True if the connection exists, false otherwise
         */
        protected boolean isConnectionCreated(String schemaName) {
            return connections.containsKey(schemaName);
        } // isConnectionCreated

        /**
         * Gets the number of connections created.
         * @return The number of connections created
         */
        protected int numConnectionsCreated() {
            return connections.size();
        } // numConnectionsCreated

        /**
         * Creates a PostgreSQL connection.
         * @param schemaName
         * @return A PostgreSQL connection
         * @throws Exception
         */
        private Connection createConnection(String schemaName)
            throws Exception {
            // dynamically load the PostgreSQL JDBC driver
            Class.forName(DRIVER_NAME);

            // return a connection based on the PostgreSQL JDBC driver
            String url = "jdbc:postgresql://" + this.postgresqlHost + ":" + this.postgresqlPort +"/" + schemaName;
            Properties props = new Properties();
            props.setProperty("user", this.postgresqlUsername);
            props.setProperty("password", this.postgresqlPassword);
            props.setProperty("sslmode", "disable");

            LOGGER.debug("Connecting to " + url);
            return DriverManager.getConnection(url, props);
        } // createConnection
    } // PostgreSQLDriver
} // PostgreSQLBackendImpl

