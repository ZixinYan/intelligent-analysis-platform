package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SqlQueryNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeExecutorRegistryTest {
    @Test
    void shouldGetRegisteredExecutor() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(new TestNodeExecutor()));
        assertTrue(registry.get("sql_query") instanceof TestNodeExecutor);
    }

    @Test
    void shouldThrowWhenExecutorMissing() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(new TestNodeExecutor()));
        assertThrows(BaseBusinessException.class, () -> registry.get("unknown"));
    }

    private static class TestNodeExecutor implements NodeExecutor<SqlQueryNodeConfigDTO> {
        @Override
        public String supportType() {
            return "sql_query";
        }

        @Override
        public com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO execute(NodeExecuteContextDTO context, SqlQueryNodeConfigDTO config) {
            return null;
        }

        @Override
        public ValidationResultDTO validate(SqlQueryNodeConfigDTO config) {
            return ValidationResultDTO.builder().valid(true).build();
        }

        @Override
        public com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO metadata() {
            return null;
        }
    }
}
