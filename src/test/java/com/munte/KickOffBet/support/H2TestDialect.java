package com.munte.KickOffBet.support;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/**
 * H2 reports UUID columns as JDBC BINARY, so untyped native-query results come back as byte[]
 * (PostgreSQL reports them as "uuid" and Hibernate returns java.util.UUID). This dialect restores the
 * PostgreSQL behaviour for native queries such as MatchRepository#findPreviewMatchIdsByLeagueIds*.
 */
public class H2TestDialect extends H2Dialect {

    @Override
    public JdbcType resolveSqlTypeDescriptor(String columnTypeName, int jdbcTypeCode, int precision, int scale,
                                             JdbcTypeRegistry jdbcTypeRegistry) {
        if ("UUID".equalsIgnoreCase(columnTypeName)) {
            return jdbcTypeRegistry.getDescriptor(SqlTypes.UUID);
        }
        return super.resolveSqlTypeDescriptor(columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry);
    }
}
