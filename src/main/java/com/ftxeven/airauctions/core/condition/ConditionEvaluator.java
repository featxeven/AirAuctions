package com.ftxeven.airauctions.core.condition;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.Map;

public final class ConditionEvaluator {

    private final Map<String, Optional<Condition>> cache = new ConcurrentHashMap<>();
    private final Consumer<String> onParseFailure;

    public ConditionEvaluator(Consumer<String> onParseFailure) {
        this.onParseFailure = onParseFailure;
    }

    // the list itself is an implicit AND chain
    public boolean evaluate(List<String> conditions, Function<String, String> placeholders) {
        for (String raw : conditions) {
            if (!evaluate(raw, placeholders)) {
                return false;
            }
        }
        return true;
    }

    public boolean evaluate(String raw, Function<String, String> placeholders) {
        Optional<Condition> parsed = cache.computeIfAbsent(raw, this::tryParse);
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
            case CONTAINS -> asString(c.left(), placeholders).contains(asString(c.right(), placeholders));
            case NOT_CONTAINS -> !asString(c.left(), placeholders).contains(asString(c.right(), placeholders));
            case STARTS_WITH -> asString(c.left(), placeholders).startsWith(asString(c.right(), placeholders));
            case ENDS_WITH -> asString(c.left(), placeholders).endsWith(asString(c.right(), placeholders));
            case GREATER -> asNumber(c.left(), placeholders) > asNumber(c.right(), placeholders);
            case LESS -> asNumber(c.left(), placeholders) < asNumber(c.right(), placeholders);
            case GREATER_OR_EQUAL -> asNumber(c.left(), placeholders) >= asNumber(c.right(), placeholders);
            case LESS_OR_EQUAL -> asNumber(c.left(), placeholders) <= asNumber(c.right(), placeholders);
            case EQUALS -> equals(c.left(), c.right(), placeholders, false);
            case NOT_EQUALS -> !equals(c.left(), c.right(), placeholders, false);
            case EQUALS_CI -> equals(c.left(), c.right(), placeholders, true);
            case NOT_EQUALS_CI -> !equals(c.left(), c.right(), placeholders, true);
        };
    }

    // numeric compare when both sides parse as numbers, so e.g. "10.0" matches "10"
    // otherwise falls back to a string compare
    private boolean equals(Condition.Expr left, Condition.Expr right, Function<String, String> placeholders, boolean ci) {
        String leftText = asString(left, placeholders);
        String rightText = asString(right, placeholders);
        double leftNum = parseNumber(leftText);
        double rightNum = parseNumber(rightText);
        if (!Double.isNaN(leftNum) && !Double.isNaN(rightNum)) {
            return leftNum == rightNum;
        }
        return ci ? leftText.equalsIgnoreCase(rightText) : leftText.equals(rightText);
    }

    private double asNumber(Condition.Expr expr, Function<String, String> placeholders) {
        return switch (expr) {
            case Condition.Expr.Literal l -> parseNumber(l.text());
            case Condition.Expr.Placeholder p -> parseNumber(resolve(p.key(), placeholders));
            case Condition.Expr.BinaryOp op -> applyArith(op, placeholders);
        };
    }

    private String asString(Condition.Expr expr, Function<String, String> placeholders) {
        return switch (expr) {
            case Condition.Expr.Literal l -> l.text();
            case Condition.Expr.Placeholder p -> resolve(p.key(), placeholders);
            case Condition.Expr.BinaryOp op -> formatNumber(applyArith(op, placeholders));
        };
    }

    private double applyArith(Condition.Expr.BinaryOp op, Function<String, String> placeholders) {
        double left = asNumber(op.left(), placeholders);
        double right = asNumber(op.right(), placeholders);
        return switch (op.operator()) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> left / right;
            case MODULO -> left % right;
        };
    }

    private static String resolve(String key, Function<String, String> placeholders) {
        String value = placeholders.apply(key);
        return value != null ? value : "";
    }

    private static double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String formatNumber(double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}