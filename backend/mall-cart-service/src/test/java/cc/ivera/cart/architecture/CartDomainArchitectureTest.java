package cc.ivera.cart.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构约束回归：购物车域四层依赖方向。
 */
class CartDomainArchitectureTest {

    private static final String PKG = "cc.ivera.cart";

    private final JavaClasses classes = new ClassFileImporter().importPackages(PKG);

    @Test
    void domainMustNotDependOnFrameworksOrOuterLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage(PKG + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "org.apache.ibatis..",
                "com.baomidou..",
                PKG + ".infrastructure..",
                PKG + ".interfaces..",
                PKG + ".application.."
            )
            .because("domain 层只允许依赖领域模型、枚举与端口接口");
        rule.check(classes);
    }

    @Test
    void applicationMustNotDependOnInterfacesOrPo() {
        ArchRule rule = noClasses()
            .that().resideInAPackage(PKG + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                PKG + ".interfaces..",
                PKG + ".infrastructure.persistence.po.."
            )
            .because("application 层只能依赖 domain 端口，不得反向引用 interfaces 或 PO");
        rule.check(classes);
    }
}
