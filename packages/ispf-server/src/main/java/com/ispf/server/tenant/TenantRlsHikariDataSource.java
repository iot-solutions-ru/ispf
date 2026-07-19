package com.ispf.server.tenant;

import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

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

        private RlsConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name)) {
                try {
                    TenantRlsSession.clear(delegate);
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
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            return method.invoke(delegate, args);
        }
    }
}
