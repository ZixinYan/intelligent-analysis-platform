package com.kuaishou.intelligentanalysisplatform.infra.observability;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * SQL 拦截日志器，等价于 MyBatis Interceptor，但作用于 Spring JDBC 层。
 *
 * <p>拦截两路 DataSource：
 * <ul>
 *   <li>Spring 管理的主 {@code dataSource}（Repository 层），通过 {@link BeanPostProcessor} 自动代理。</li>
 *   <li>{@code HikariPoolRegistry} 管理的用户查询连接池，调用方显式调用 {@link #wrap} 方法。</li>
 * </ul>
 *
 * 在 {@code PreparedStatement.execute*()} 处记录 SQL 语句和执行耗时。
 */
@Component
public class SqlLogInterceptor implements BeanPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SqlLogInterceptor.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** 供 HikariPoolRegistry 显式调用，为用户查询连接池包装日志代理。 */
    public DataSource wrap(DataSource target, String datasourceId) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (DataSource) Proxy.newProxyInstance(cl, new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    Object result = invoke(target, method, args);
                    if ("getConnection".equals(method.getName()) && result instanceof Connection conn) {
                        return wrapConnection(conn, datasourceId);
                    }
                    return result;
                });
    }

    /** BeanPostProcessor 回调：自动包装 Spring 主 dataSource Bean。 */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("dataSource".equals(beanName) && bean instanceof DataSource ds) {
            return wrap(ds, "app");
        }
        return bean;
    }

    private Connection wrapConnection(Connection conn, String datasourceId) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class},
                (proxy, method, args) -> {
                    Object result = invoke(conn, method, args);
                    if ("prepareStatement".equals(method.getName())
                            && args != null && args.length > 0
                            && result instanceof PreparedStatement stmt) {
                        return wrapPreparedStatement(stmt, String.valueOf(args[0]), datasourceId);
                    }
                    return result;
                });
    }

    private PreparedStatement wrapPreparedStatement(PreparedStatement stmt, String sql, String datasourceId) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (!method.getName().startsWith("execute")) {
                        return invoke(stmt, method, args);
                    }
                    long start = System.currentTimeMillis();
                    try {
                        Object result = invoke(stmt, method, args);
                        log.info("SQL executed: datasourceId={}, method={}, elapsedMs={}, sql={}",
                                datasourceId, method.getName(), System.currentTimeMillis() - start, sql);
                        return result;
                    } catch (Exception e) {
                        log.warn("SQL failed: datasourceId={}, method={}, elapsedMs={}, sql={}, error={}",
                                datasourceId, method.getName(), System.currentTimeMillis() - start, sql, e.getMessage());
                        throw e;
                    }
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }
}
