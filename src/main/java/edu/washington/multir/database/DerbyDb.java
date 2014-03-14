package edu.washington.multir.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Derby wrapper class
 * @author jgilme1
 *
 */
public class DerbyDb {
    public static final String PROTOCOL = "jdbc:derby:";
    public static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
    protected Connection connection = null;

    /**
* Creates a new Derby DB connection.
*
* @param url The URL of the database (e.g., localhost:1527://path/to/db)
     * @throws SQLException 
*/
    public DerbyDb(String dbName) throws SQLException {
        String connectionUrl = PROTOCOL + dbName+";create=true";
        try {
            Class.forName(DRIVER);
            connection = DriverManager.getConnection(connectionUrl);
        } catch (SQLException e) {
            throw new RuntimeException("Could not open Derby DB at " + connectionUrl, e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find Derby driver " + DRIVER, e);
        }
    }
    /**
* Closes the Derby database connection.
*/
    public void cleanUp() {
      try {
        connection.close();
      } catch (SQLException e) {
        throw new RuntimeException("Error closing DB connection.", e);
      }
    }
    
    public void startConnection(String dbName){
    	String connectionUrl = PROTOCOL + dbName;
    	try{
    		connection = DriverManager.getConnection(connectionUrl);
    	}
    	catch (SQLException e){
    		throw new RuntimeException("Error opening DB connection", e);
    	}
    }
}
