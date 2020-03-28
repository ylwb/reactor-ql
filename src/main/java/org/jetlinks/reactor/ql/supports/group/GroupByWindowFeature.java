package org.jetlinks.reactor.ql.supports.group;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.reactor.ql.ReactorQLMetadata;
import org.jetlinks.reactor.ql.feature.FeatureId;
import org.jetlinks.reactor.ql.feature.FilterFeature;
import org.jetlinks.reactor.ql.feature.GroupFeature;
import org.jetlinks.reactor.ql.utils.CastUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 窗口函数
 * <pre>
 * group by _window(10) => flux.window(10)
 * <p>
 * group by _window('1s') => flux.window(Duration.ofSeconds(1))
 * </pre>
 *
 * @author zhouhao
 * @see 1.0
 */
@Slf4j
public class GroupByWindowFeature implements GroupFeature {

    static String ID = FeatureId.GroupBy.of("_window").getId();

    @Override
    public <T> Function<Flux<T>, Flux<? extends Flux<T>>> createGroupMapper(Expression expression, ReactorQLMetadata metadata) {

        net.sf.jsqlparser.expression.Function windowFunc = ((net.sf.jsqlparser.expression.Function) expression);

        ExpressionList parameters = windowFunc.getParameters();
        List<Expression> expressions;
        if (parameters == null || CollectionUtils.isEmpty(expressions = parameters.getExpressions())) {
            throw new UnsupportedOperationException("窗口函数必须传入参数,如: window('10s') , window(30)");
        }
        try {
            if (expressions.size() == 1) {
                return createOneParameter(expressions, metadata);
            } else if (expressions.size() == 2) {
                return createTwoParameter(expressions, metadata);
            }
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("不支持的函数[ " + expression + " ] : " + e.getMessage(), e);
        }
        throw new UnsupportedOperationException("函数[ " + expression + " ]参数数量错误,最小1,最大2.");
    }

    protected <T> Function<Flux<T>, Flux<? extends Flux<T>>> createOneParameter(List<Expression> expressions, ReactorQLMetadata metadata) {
        Expression expr = expressions.get(0);
        // window(100)
        if (expr instanceof LongValue) {
            int val = (int) ((LongValue) expr).getValue();
            return flux -> flux.window((val));
        }
        if (expr instanceof StringValue) {
            Duration duration = CastUtils.parseDuration(((StringValue) expr).getValue());
            if (duration.toMillis() <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0:" + expr);
            }
            return flux -> flux.window(duration);
        }
        BiPredicate<Object, Object> predicate = FilterFeature.createPredicateNow(expr, metadata);
        return flux -> flux.windowUntil(v -> predicate.test(v, v));
    }

    protected <T> Function<Flux<T>, Flux<? extends Flux<T>>> createTwoParameter(List<Expression> expressions, ReactorQLMetadata metadata) {
        Expression first = expressions.get(0);
        Expression second = expressions.get(1);

        // window(100,10)
        if (first instanceof LongValue && second instanceof LongValue) {
            int val = (int) ((LongValue) first).getValue();
            int secondVal = (int) ((LongValue) second).getValue();
            return flux -> flux.window(val, secondVal);
        }
        // window('1s','10s')
        if (first instanceof StringValue && second instanceof StringValue) {
            Duration duration = CastUtils.parseDuration(((StringValue) first).getValue());
            Duration secondDuration = CastUtils.parseDuration(((StringValue) first).getValue());
            if (duration.toMillis() <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0: " + first);
            }
            return flux -> flux.window(duration, secondDuration);
        }
        throw new UnsupportedOperationException("不支持的参数: " + first + " , " + second);
    }

    @Override
    public String getId() {
        return ID;
    }
}