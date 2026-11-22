package cc.ivera.refund.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构约束回归：退款域 domain 层不得依赖 Spring / MyBatis-Plus / infrastructure / interfaces / application。
 * <p>对应 AGENTS.md 第 4 节的依赖方向约束，防止领域模型被框架与上层实现污染。</p>
 */
class RefundDomainArchitectureTest {

    private final JavaClasses domainClasses = new ClassFileImporter().importPackages("cc.ivera.refund.domain");

    @Test
    void domainMustNotDependOnFrameworksOrOuterLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("cc.ivera.refund.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "org.apache.ibatis..",
                "com.baomidou..",
                "cc.ivera.refund.infrastructure..",
                "cc.ivera.refund.interfaces..",
                "cc.ivera.refund.application.."
            )
            .because("domain 层只允许依赖领域模型、枚举与端口接口");
        rule.check(domainClasses);
    }
}
