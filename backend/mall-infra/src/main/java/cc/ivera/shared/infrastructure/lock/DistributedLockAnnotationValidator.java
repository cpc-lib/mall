package cc.ivera.shared.infrastructure.lock;

import cc.ivera.shared.domain.lock.DistributedLock;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动期校验 {@link DistributedLock} 是否真的会加锁，避免出现“注解在、锁不在”的静默丢锁。
 * <p>
 * 覆盖三类失效场景：
 * <ol>
 *   <li>当前进程没有 {@link DistributedLockTemplate}（例如排除了 Redisson 依赖的服务）；</li>
 *   <li>注解标注在 private / static / final 方法上，Spring AOP 无法代理；</li>
 *   <li>含注解的 Bean 未被 AOP 代理。</li>
 * </ol>
 * 只要存在上述情况就直接启动失败，而不是让业务在无锁状态下继续运行。
 */
@Slf4j
@Component
public class DistributedLockAnnotationValidator implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;

    public DistributedLockAnnotationValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean lockAvailable = applicationContext.getBeanNamesForType(DistributedLockTemplate.class).length > 0;
        List<String> violations = new ArrayList<>();
        int methodCount = 0;

        for (String beanName : applicationContext.getBeanNamesForType(Object.class, false, false)) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(beanType);
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(userClass)) {
                if (!AnnotatedElementUtils.hasAnnotation(method, DistributedLock.class)) {
                    continue;
                }
                methodCount++;
                String location = userClass.getName() + "#" + method.getName();
                if (!lockAvailable) {
                    violations.add(location + "：当前进程没有 DistributedLockTemplate（检查 Redisson 依赖是否被排除）");
                } else if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())
                    || Modifier.isFinal(method.getModifiers())) {
                    violations.add(location + "：方法必须是非 static、非 private、非 final 才能被切面代理");
                } else if (!AopUtils.isAopProxy(applicationContext.getBean(beanName))) {
                    violations.add(location + "：所在 Bean 未被 AOP 代理");
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("分布式锁注解无法生效：" + String.join("；", violations));
        }
        log.info("分布式锁注解校验通过，注解方法数={}，锁模板可用={}", methodCount, lockAvailable);
    }
}
