package cc.ivera.order.infrastructure.config;

import org.apache.seata.saga.engine.StateMachineConfig;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.DbStateMachineConfig;
import org.apache.seata.saga.engine.expression.ExpressionFactoryManager;
import org.apache.seata.saga.engine.expression.exception.ExceptionMatchExpressionFactory;
import org.apache.seata.saga.engine.expression.seq.SequenceExpressionFactory;
import org.apache.seata.saga.engine.expression.spel.SpringELExpressionFactory;
import org.apache.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.apache.seata.saga.engine.sequence.UUIDSeqGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Seata Saga 状态机配置：使用主数据源（DM8）持久化状态机定义与实例。
 * starter 默认不加载 statelang 资源，这里显式声明状态机定义资源与表达式工厂；
 * 表达式统一走 Spring EL，不启用 Groovy 脚本，避免 JDK 21 下的脚本编译兼容问题。
 */
@Configuration
public class SeataSagaConfig {

    @Bean
    public StateMachineConfig stateMachineConfig(DataSource dataSource,
                                                 ApplicationContext applicationContext,
                                                 @Value("${seata.application-id}") String applicationId,
                                                 @Value("${seata.tx-service-group}") String txServiceGroup) {
        // DbStateMachineConfig.afterPropertiesSet 会从 Seata 配置覆盖 sagaBranchRegisterEnable，
        // 这里在其后再关闭分支注册：采用客户端补偿，失败后由 CheckoutSagaService 显式 compensate，
        // 避免 TC 对 UN 实例反复 branchCommit -> forward 重试。
        DbStateMachineConfig config = new DbStateMachineConfig() {
            @Override
            public void afterPropertiesSet() throws Exception {
                super.afterPropertiesSet();
                setSagaBranchRegisterEnable(false);
            }
        };
        config.setDataSource(dataSource);
        config.setApplicationId(applicationId);
        config.setTxServiceGroup(txServiceGroup);
        config.setResources(new String[]{"statelang/*.json"});
        config.setEnableAsync(false);
        config.setSagaJsonParser("jackson");
        config.setExpressionFactoryManager(expressionFactoryManager(applicationContext));
        return config;
    }

    @Bean
    public StateMachineEngine stateMachineEngine(StateMachineConfig stateMachineConfig) {
        ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
        engine.setStateMachineConfig(stateMachineConfig);
        return engine;
    }

    private ExpressionFactoryManager expressionFactoryManager(ApplicationContext applicationContext) {
        ExpressionFactoryManager manager = new ExpressionFactoryManager();
        manager.putExpressionFactory(ExpressionFactoryManager.DEFAULT_EXPRESSION_TYPE,
            new SpringELExpressionFactory(applicationContext));
        SequenceExpressionFactory sequenceFactory = new SequenceExpressionFactory();
        sequenceFactory.setSeqGenerator(new UUIDSeqGenerator());
        manager.putExpressionFactory("Sequence", sequenceFactory);
        manager.putExpressionFactory("Exception", new ExceptionMatchExpressionFactory());
        return manager;
    }
}
