package com.valorsky.skysignals.database;
import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class EventRepositoryTest {
    @Test void repositoryCanUseConnectionEstablishedAfterConstruction() throws Exception {
        var source = new AtomicReference<DataSource>();
        var repository = new EventRepository(source::get, Logger.getAnonymousLogger());
        repository.hasBeenClaimed("first");
        var dataSource = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class); var result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        source.set(dataSource); repository.hasBeenClaimed("second");
        verify(statement).setString(1, "second");
    }
}
