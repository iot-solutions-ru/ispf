package com.ispf.server.tenant;

import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
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

    private Connection wrap(Connection connection) throws SQLException {
        TenantRlsSession.apply(connection, TenantRlsContext.get());
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new RlsConnectionHandler(this, connection)
        );
    }

    /** Package-visible for unit tests that drive the proxy without a live pool. */
    Connection wrapForTests(Connection connection) throws SQLException {
        return wrap(connection);
    }

    static Object invokeUnwrapped(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getTargetException();
            throw cause != null ? cause : ex;
        }
    }

    private static final class RlsConnectionHandler implements InvocationHandler {
        private final HikariDataSource pool;
        private final Connection delegate;
        private final Set<String> scriptGucs = new LinkedHashSet<>();

        private RlsConnectionHandler(HikariDataSource pool, Connection delegate) {
            this.pool = pool;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name)) {
                return closeAndClear();
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
            Object result = invokeUnwrapped(delegate, method, args);
            if (result instanceof Statement statement && name.startsWith("create")) {
                return wrapStatement(statement);
            }
            return result;
        }

        private Object closeAndClear() throws Throwable {
            boolean cleared = false;
            try {
                // Session-scoped set_config issued inside an open/aborted transaction is undone by
                // Hikari's close-time rollback. End the transaction first so clear() sticks.
                if (!delegate.getAutoCommit()) {
                    try {
                        delegate.rollback();
                    } catch (SQLException ignored) {
                        // still attempt clear / return
                    }
                }
                TenantRlsSession.clear(delegate, scriptGucs);
                cleared = true;
            } catch (SQLException clearFailed) {
                try {
                    pool.evictConnection(delegate);
                } catch (RuntimeException ignored) {
                    // fall through to close
                }
            }
            try {
                return invokeUnwrapped(delegate, Connection.class.getMethod("close"), null);
            } catch (Throwable closeFailed) {
                if (!cleared) {
                    try {
                        pool.evictConnection(delegate);
                    } catch (RuntimeException ignored) {
                        // best effort
                    }
                }
                throw closeFailed;
            }
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
                        return invokeUnwrapped(statement, method, args);
                    }
            );
        }

        private static boolean isPreparedSql(String name) {
            return "prepareStatement".equals(name) || "prepareCall".equals(name);
        }
    }
}
