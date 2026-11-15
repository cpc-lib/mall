package cc.ivera.shared.infrastructure.lock;

import cc.ivera.shared.domain.lock.DistributedLock;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;

/**
 * 注册 {@link DistributedLock} 的 AOP 切面。
 * <p>
 * 优先级高于事务通知，保证事务在锁持有期间开始并提交。
 * 使用 {@link StaticMethodMatcherPointcut} 直接匹配方法注解，不经过 AspectJ 表达式解析，
 * 避免在部分 AspectJ 版本下 Pointcut 解析失败而导致注解被静默忽略。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedissonClient.class)
public class DistributedLockConfiguration {

    @Bean
    public Advisor distributedLockAdvisor(DistributedLockTemplate distributedLockTemplate) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
            new DistributedLockPointcut(), new DistributedLockInterceptor(distributedLockTemplate));
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }

    private static final class DistributedLockPointcut extends StaticMethodMatcherPointcut {

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            Method specificMethod = AopUtils.getMostSpecificMethod(method, ClassUtils.getUserClass(targetClass));
            return AnnotatedElementUtils.hasAnnotation(specificMethod, DistributedLock.class);
        }
    }
}
