package com.ispf.server.relational;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * ADR-0080: ojdbc rejects {@link UUID} parameters (ORA-17004) and {@code getObject(…, UUID.class)}.
 * The Oracle pack keeps PostgreSQL {@code uuid} columns as {@code VARCHAR2(36 CHAR)}, so UUIDs are
 * bound and read as their canonical text. Every other call passes through unchanged.
 */
public final class OracleUuidJdbcDataSource extends DelegatingDataSource {

    public OracleUuidJdbcDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(Connection.class, super.getConnection(), null);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(Connection.class, super.getConnection(username, password), null);
    }

    @SuppressWarnings("unchecked")
    static <T> T wrap(Class<T> type, T target, Connection connection) {
        if (target == null) {
            return null;
        }
        return (T) Proxy.newProxyInstance(
                OracleUuidJdbcDataSource.class.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> invoke(proxy, target, method, args, connection)
        );
    }

    @SuppressWarnings("ReferenceEquality") // proxies compare by identity, like the pooled connections they wrap
    private static Object invoke(Object proxy, Object target, Method method, Object[] args, Connection connection)
            throws Throwable {
        String name = method.getName();
        int arity = args == null ? 0 : args.length;
        if (name.equals("equals") && arity == 1) {
            return proxy == args[0];
        }
        if (name.equals("hashCode") && arity == 0) {
            return System.identityHashCode(proxy);
        }
        if (name.equals("setObject") && arity >= 2 && args[1] instanceof UUID uuid) {
            bindText((PreparedStatement) target, args[0], uuid.toString());
            return null;
        }
        if (name.equals("getObject") && arity == 2 && args[1] == UUID.class && target instanceof ResultSet rs) {
            String text = args[0] instanceof Integer index ? rs.getString(index) : rs.getString((String) args[0]);
            return text == null ? null : UUID.fromString(text);
        }
        if (name.equals("getConnection") && arity == 0 && connection != null) {
            return connection;
        }
        Object result;
        try {
            result = method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
        Connection owner = target instanceof Connection ? (Connection) proxy : connection;
        if (result instanceof CallableStatement statement && method.getReturnType() == CallableStatement.class) {
            return wrap(CallableStatement.class, statement, owner);
        }
        if (result instanceof PreparedStatement statement && method.getReturnType() == PreparedStatement.class) {
            return wrap(PreparedStatement.class, statement, owner);
        }
        if (result instanceof Statement statement && method.getReturnType() == Statement.class) {
            return wrap(Statement.class, statement, owner);
        }
        if (result instanceof ResultSet resultSet && method.getReturnType() == ResultSet.class) {
            return wrap(ResultSet.class, resultSet, owner);
        }
        return result;
    }

    private static void bindText(PreparedStatement statement, Object parameter, String text) throws SQLException {
        if (parameter instanceof Integer index) {
            statement.setString(index, text);
        } else {
            ((CallableStatement) statement).setString((String) parameter, text);
        }
    }
}
