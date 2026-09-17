package com.valorsky.skysignals.database.migration;

import java.sql.Connection;
import java.sql.Statement;

public record Migration(int version, String description, String sql) {

    public void execute(Connection conn) throws java.sql.SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
