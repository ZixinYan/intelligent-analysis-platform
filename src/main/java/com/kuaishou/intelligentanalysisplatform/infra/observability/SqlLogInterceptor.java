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
 * Spring JDBC 的 SQL 拦截日志器，等价于 MyBatis Interceptor。
 *
 * <p>通过 {@link BeanPostProcessor} 代理 Spring 管理的主 {@code dataSource} Bean，
 * 在 {@code prepareStatement → execute*} 链路上注入日志，记录 SQL 语句和执行耗时。
 * 仅拦截 Repository 层的 SQL，不影响 {@code HikariPoolRegistry} 管理的用户查询连接池。
 */
@Component
public class SqlLogInterceptor implements BeanPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SqlLogInterceptor.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!"dataSource".equals(beanName) || !(bean instanceof DataSource)) {
            return bean;
        }
        DataSource original = (DataSource) bean;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (DataSource) Proxy.newProxyInstance(cl, new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    Object result = invoke(original, method, args);
                    if ("getConnection".equals(method.getName()) && result instanceof Connection conn) {
                        return wrapConnection(conn);
                    }
                    return result;
                });
    }

    private Connection wrapConnection(Connection conn) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class},
                (proxy, method, args) -> {
                    Object result = invoke(conn, method, args);
                    if ("prepareStatement".equals(method.getName())
                            && args != null && args.length > 0
                            && result instanceof PreparedStatement stmt) {
                        return wrapPreparedStatement(stmt, String.valueOf(args[0]));
                    }
                    return result;
                });
    }

    private PreparedStatement wrapPreparedStatement(PreparedStatement stmt, String sql) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (!method.getName().startsWith("execute")) {
                        return invoke(stmt, method, args);
                    }
                    long start = System.currentTimeMillis();
                    try {
                        Object result = invoke(stmt, method, args);
                        log.info("Repository SQL: method={}, elapsedMs={}, sql={}",
                                method.getName(), System.currentTimeMillis() - start, sql);
                        return result;
                    } catch (Exception e) {
                        log.warn("Repository SQL failed: method={}, elapsedMs={}, sql={}, error={}",
                                method.getName(), System.currentTimeMillis() - start, sql, e.getMessage());
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
