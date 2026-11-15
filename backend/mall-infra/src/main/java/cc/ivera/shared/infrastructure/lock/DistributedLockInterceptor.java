package cc.ivera.shared.infrastructure.lock;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLock;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * {@link DistributedLock} 拦截器：复用 {@link DistributedLockTemplate}，保证注解方式与编程式加锁
 * 使用同一套 Redisson watchdog 与全局围栏令牌语义。
 * <p>
 * 不依赖 AspectJ 表达式解析，由 {@link DistributedLockConfiguration} 中的
 * {@code StaticMethodMatcherPointcut} 直接匹配方法注解，避免 Pointcut 解析失败导致注解被静默忽略。
 */
public class DistributedLockInterceptor implements MethodInterceptor {

    private final DistributedLockTemplate distributedLockTemplate;

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockInterceptor(DistributedLockTemplate distributedLockTemplate) {
        this.distributedLockTemplate = distributedLockTemplate;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = ClassUtils.getUserClass(invocation.getThis());
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
        DistributedLock distributedLock = AnnotatedElementUtils.findMergedAnnotation(method, DistributedLock.class);
        if (distributedLock == null) {
            return invocation.proceed();
        }
        String lockKey = resolveLockKey(method, invocation.getArguments(), distributedLock.key());
        if (!StringUtils.hasText(lockKey)) {
            throw new BizException("分布式锁key不能为空");
        }
        try {
            return distributedLockTemplate.execute(lockKey, distributedLock.waitTimeMillis(),
                distributedLock.holdWarnMillis(), () -> {
                try {
                    return invocation.proceed();
                } catch (Throwable throwable) {
                    throw new LockInvocationException(throwable);
                }
            });
        } catch (LockInvocationException e) {
            throw e.getTargetException();
        }
    }

    private String resolveLockKey(Method method, Object[] args, String expression) {
        if (expression == null || !expression.contains("#")) {
            return expression;
        }
        MethodBasedEvaluationContext context =
            new MethodBasedEvaluationContext(null, method, args, parameterNameDiscoverer);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        Object value = expressionParser.parseExpression(expression).getValue(context);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 把目标方法的受检异常包装后穿过 {@code Supplier}，再在外层还原为原始异常。
     */
    private static final class LockInvocationException extends RuntimeException {

        private LockInvocationException(Throwable cause) {
            super(cause);
        }

        private Throwable getTargetException() {
            return getCause();
        }
    }
}
