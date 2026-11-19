package com.ispf.server.tenant;

import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HikariDataSource that applies PostgreSQL RLS GUCs from {@link TenantRlsContext}
 * on checkout and resets them on close (return to pool).
 */
public class TenantRlsHikariDataSource extends HikariDataSource {

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private static Connection wrap(Connection connection) throws SQLException {
        TenantRlsSession.apply(connection, TenantRlsContext.get());
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new RlsConnectionHandler(connection)
        );
    }

    private static final class RlsConnectionHandler implements InvocationHandler {
        private final Connection delegate;
        private final Set<String> scriptGucs = new LinkedHashSet<>();

        private RlsConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name)) {
                try {
                    TenantRlsSession.clear(delegate, scriptGucs);
                } catch (SQLException ignored) {
                    // still return connection to pool
                }
                return method.invoke(delegate, args);
            }
            if ("unwrap".equals(name)) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    return proxy;
                }
                return delegate.unwrap(iface);
            }
            if ("isWrapperFor".equals(name)) {
                Class<?> iface = (Class<?>) args[0];
                return iface.isInstance(proxy) || delegate.isWrapperFor(iface);
            }
            if ("equals".equals(name)) {
                @SuppressWarnings("ReferenceEquality")
                boolean same = proxy == args[0];
                return same;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if (args != null && args.length > 0 && args[0] instanceof String sql && isPreparedSql(name)) {
                TenantRlsSession.collectCustomGucs(sql, scriptGucs);
            }
            Object result = method.invoke(delegate, args);
            if (result instanceof Statement statement && name.startsWith("create")) {
                return wrapStatement(statement);
            }
            return result;
        }

        private Statement wrapStatement(Statement statement) {
            Class<?> iface = statement instanceof CallableStatement
                    ? CallableStatement.class
                    : statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(
                    iface.getClassLoader(),
                    new Class<?>[]{iface},
                    (proxy, method, args) -> {
                        if (args != null && args.length > 0 && args[0] instanceof String sql
                                && method.getName().startsWith("execute")) {
                            TenantRlsSession.collectCustomGucs(sql, scriptGucs);
                        }
                        return method.invoke(statement, args);
                    }
            );
        }

        private static boolean isPreparedSql(String name) {
            return "prepareStatement".equals(name) || "prepareCall".equals(name);
        }
    }
}
