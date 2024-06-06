package com.felix;

import org.rapidoid.setup.App;
import org.rapidoid.setup.On;
import org.rapidoid.u.U;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {
    public static void main(String[] args) throws SQLException {
        App.bootstrap(args);
        Connection connection = HikariCPDataSource.getConnection();
        Statement stmt = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS  Registration " +
                "(id INTEGER not NULL, " +
                " first VARCHAR(255), " +
                " last VARCHAR(255), " +
                " age INTEGER, " +
                " PRIMARY KEY ( id ))";
        stmt.executeUpdate(sql);

        // STEP 3: Execute a query
        stmt = connection.createStatement();
        sql = "INSERT OR IGNORE INTO Registration " + "VALUES (100, 'Zara', 'Ali', 18)";

        stmt.executeUpdate(sql);
        sql = "INSERT  OR IGNORE INTO Registration " + "VALUES (101, 'Mahnaz', 'Fatma', 25)";

        stmt.executeUpdate(sql);
        sql = "INSERT  OR IGNORE INTO Registration " + "VALUES (102, 'Zaid', 'Khan', 30)";

        stmt.executeUpdate(sql);
        sql = "INSERT OR IGNORE INTO Registration " + "VALUES(103, 'Sumit', 'Mittal', 28)";

        stmt.executeUpdate(sql);


        stmt = connection.createStatement();
        sql = "SELECT id, first, last, age FROM Registration";
        ResultSet rs = stmt.executeQuery(sql);

        // STEP 4: Extract data from result set
        while (rs.next()) {
            // Retrieve by column name
            int id = rs.getInt("id");
            int age = rs.getInt("age");
            String first = rs.getString("first");
            String last = rs.getString("last");

            // Display values
            System.out.print("ID: " + id);
            System.out.print(", Age: " + age);
            System.out.print(", First: " + first);
            System.out.println(", Last: " + last);
        }
        On.get("/").json(() -> U.map("key", "value"));
    }
}