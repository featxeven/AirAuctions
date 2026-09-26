package com.ftxeven.airauctions.core.condition;

import com.ftxeven.airauctions.core.cache.BoundedCache;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ConditionEvaluator {

    private static final int MAX_CACHED_CONDITIONS = 4096;

    private final BoundedCache<String, Optional<Condition>> cache = new BoundedCache<>(MAX_CACHED_CONDITIONS);
    private final Consumer<String> onParseFailure;

    public ConditionEvaluator(Consumer<String> onParseFailure) {
        this.onParseFailure = onParseFailure;
    }

    public boolean evaluate(List<String> conditions, Function<String, String> placeholders) {
        for (String raw : conditions) {
            if (!evaluate(raw, placeholders)) {
                return false;
            }
        }
        return true;
    }

    public boolean evaluate(String raw, Function<String, String> placeholders) {
        Optional<Condition> parsed = cache.get(raw, this::tryParse);
        return parsed.isPresent() && test(parsed.get(), placeholders);
    }

    private Optional<Condition> tryParse(String raw) {
        try {
            return Optional.of(ConditionParser.parse(raw));
        } catch (Exception e) {
            onParseFailure.accept("Condition '" + raw + "' failed to parse: " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean test(Condition condition, Function<String, String> placeholders) {
        return switch (condition) {
            case Condition.Comparison c -> compare(c, placeholders);
            case Condition.And and -> and.parts().stream().allMatch(p -> test(p, placeholders));
            case Condition.Or or -> or.parts().stream().anyMatch(p -> test(p, placeholders));
        };
    }

    private boolean compare(Condition.Comparison c, Function<String, String> placeholders) {
        return switch (c.operator()) {
            case CONTAINS -> ExprEvaluator.asString(c.left(), placeholders).contains(ExprEvaluator.asString(c.right(), placeholders));
            case NOT_CONTAINS -> !ExprEvaluator.asString(c.left(), placeholders).contains(ExprEvaluator.asString(c.right(), placeholders));
            case STARTS_WITH -> ExprEvaluator.asString(c.left(), placeholders).startsWith(ExprEvaluator.asString(c.right(), placeholders));
            case ENDS_WITH -> ExprEvaluator.asString(c.left(), placeholders).endsWith(ExprEvaluator.asString(c.right(), placeholders));
            case GREATER -> ExprEvaluator.asNumber(c.left(), placeholders) > ExprEvaluator.asNumber(c.right(), placeholders);
            case LESS -> ExprEvaluator.asNumber(c.left(), placeholders) < ExprEvaluator.asNumber(c.right(), placeholders);
            case GREATER_OR_EQUAL -> ExprEvaluator.asNumber(c.left(), placeholders) >= ExprEvaluator.asNumber(c.right(), placeholders);
            case LESS_OR_EQUAL -> ExprEvaluator.asNumber(c.left(), placeholders) <= ExprEvaluator.asNumber(c.right(), placeholders);
            case EQUALS -> equals(c.left(), c.right(), placeholders, false);
            case NOT_EQUALS -> !equals(c.left(), c.right(), placeholders, false);
            case EQUALS_CI -> equals(c.left(), c.right(), placeholders, true);
            case NOT_EQUALS_CI -> !equals(c.left(), c.right(), placeholders, true);
        };
    }

    private boolean equals(Condition.Expr left, Condition.Expr right, Function<String, String> placeholders, boolean ci) {
        String leftText = ExprEvaluator.asString(left, placeholders);
        String rightText = ExprEvaluator.asString(right, placeholders);
        double leftNum = ExprEvaluator.parseNumber(leftText);
        double rightNum = ExprEvaluator.parseNumber(rightText);
        if (!Double.isNaN(leftNum) && !Double.isNaN(rightNum)) {
            return leftNum == rightNum;
        }
        return ci ? leftText.equalsIgnoreCase(rightText) : leftText.equals(rightText);
    }
}