package com.ftxeven.airauctions.core.condition;

import java.util.function.Function;

public final class ExprEvaluator {

    private ExprEvaluator() {
    }

    public static double asNumber(Condition.Expr expr, Function<String, String> placeholders) {
        return switch (expr) {
            case Condition.Expr.Literal l -> parseNumber(l.text());
            case Condition.Expr.Placeholder p -> parseNumber(resolve(p.key(), placeholders));
            case Condition.Expr.BinaryOp op -> applyArith(op, placeholders);
        };
    }

    public static String asString(Condition.Expr expr, Function<String, String> placeholders) {
        return switch (expr) {
            case Condition.Expr.Literal l -> l.text();
            case Condition.Expr.Placeholder p -> resolve(p.key(), placeholders);
            case Condition.Expr.BinaryOp op -> formatNumber(applyArith(op, placeholders));
        };
    }

    public static double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String formatNumber(double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static double applyArith(Condition.Expr.BinaryOp op, Function<String, String> placeholders) {
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
}