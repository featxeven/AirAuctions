package com.ftxeven.airauctions.core.condition;

import java.util.List;

public sealed interface Condition permits Condition.Comparison, Condition.And, Condition.Or {

    record Comparison(Expr left, Operator operator, Expr right) implements Condition {}

    record And(List<Condition> parts) implements Condition {
        public And {
            parts = List.copyOf(parts);
        }
    }

    record Or(List<Condition> parts) implements Condition {
        public Or {
            parts = List.copyOf(parts);
        }
    }

    enum Operator {
        EQUALS, NOT_EQUALS, EQUALS_CI, NOT_EQUALS_CI,
        GREATER, LESS, GREATER_OR_EQUAL, LESS_OR_EQUAL,
        CONTAINS, NOT_CONTAINS, STARTS_WITH, ENDS_WITH
    }

    sealed interface Expr permits Expr.Literal, Expr.Placeholder, Expr.BinaryOp {

        record Literal(String text) implements Expr {}

        record Placeholder(String key) implements Expr {}

        record BinaryOp(Expr left, ArithOperator operator, Expr right) implements Expr {}

        enum ArithOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO }
    }
}