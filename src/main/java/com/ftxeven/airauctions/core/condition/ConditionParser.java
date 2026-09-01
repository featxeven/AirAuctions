package com.ftxeven.airauctions.core.condition;

import java.util.ArrayList;
import java.util.List;

/** parses a condition line into a Condition tree - "value operator value", the "AND/OR ..."
 * prefix shorthand, and the general parenthesized form with arithmetic and nested AND/OR. */
public final class ConditionParser {

    private final List<Token> tokens;
    private int pos;

    private ConditionParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Condition parse(String raw) {
        List<Token> tokens = tokenize(raw);
        if (tokens.isEmpty()) {
            throw new SyntaxException("Empty condition");
        }
        return new ConditionParser(tokens).parseTopLevel();
    }

    // Grammar

    private Condition parseTopLevel() {
        Token first = tokens.getFirst();
        if (isWord(first, "AND")) {
            pos = 1;
            return parsePrefixForm(true);
        }
        if (isWord(first, "OR")) {
            pos = 1;
            return parsePrefixForm(false);
        }
        Condition result = parseOrExpr();
        if (pos != tokens.size()) {
            throw new SyntaxException("Unexpected trailing content near '" + tokens.get(pos).text() + "'");
        }
        return result;
    }

    private Condition parsePrefixForm(boolean and) {
        List<Condition> comparisons = new ArrayList<>();
        while (pos < tokens.size()) {
            comparisons.add(parseComparison());
        }
        if (comparisons.size() < 2) {
            throw new SyntaxException((and ? "AND" : "OR") + " prefix form requires at least two comparisons");
        }
        return and ? new Condition.And(comparisons) : new Condition.Or(comparisons);
    }

    private Condition parseOrExpr() {
        List<Condition> parts = new ArrayList<>();
        parts.add(parseAndExpr());
        while (isWord(peek(), "OR")) {
            pos++;
            parts.add(parseAndExpr());
        }
        return parts.size() == 1 ? parts.getFirst() : new Condition.Or(parts);
    }

    private Condition parseAndExpr() {
        List<Condition> parts = new ArrayList<>();
        parts.add(parseAtom());
        while (isWord(peek(), "AND")) {
            pos++;
            parts.add(parseAtom());
        }
        return parts.size() == 1 ? parts.getFirst() : new Condition.And(parts);
    }

    // a "(" here is ambiguous. It can open a nested logical group ("(a == b OR c == d)")
    // or just be arithmetic grouping inside a value ("(x + y) / z >= 1"). scanParen
    // resolves it: an arithmetic group never contains a comparison op or AND/OR.
    private Condition parseAtom() {
        if (peekType() == TokenType.LPAREN) {
            ParenSpan span = scanParen(pos);
            if (span.logical()) {
                pos++;
                Condition inner = parseOrExpr();
                expect(TokenType.RPAREN);
                return inner;
            }
        }
        return parseComparison();
    }

    private Condition parseComparison() {
        Condition.Expr left = parseArithExpr();
        Condition.Operator operator = expectCompareOp();
        Condition.Expr right = parseArithExpr();
        return new Condition.Comparison(left, operator, right);
    }

    private Condition.Expr parseArithExpr() {
        Condition.Expr left = parseTerm();
        while (true) {
            Token t = peek();
            if (isArith(t, "+")) {
                pos++;
                left = new Condition.Expr.BinaryOp(left, Condition.Expr.ArithOperator.ADD, parseTerm());
            } else if (isArith(t, "-")) {
                pos++;
                left = new Condition.Expr.BinaryOp(left, Condition.Expr.ArithOperator.SUBTRACT, parseTerm());
            } else {
                return left;
            }
        }
    }

    private Condition.Expr parseTerm() {
        Condition.Expr left = parseFactor();
        while (true) {
            Token t = peek();
            if (isArith(t, "*")) {
                pos++;
                left = new Condition.Expr.BinaryOp(left, Condition.Expr.ArithOperator.MULTIPLY, parseFactor());
            } else if (isArith(t, "/")) {
                pos++;
                left = new Condition.Expr.BinaryOp(left, Condition.Expr.ArithOperator.DIVIDE, parseFactor());
            } else if (isWord(t, "MOD")) {
                pos++;
                left = new Condition.Expr.BinaryOp(left, Condition.Expr.ArithOperator.MODULO, parseFactor());
            } else {
                return left;
            }
        }
    }

    private Condition.Expr parseFactor() {
        Token t = advance();
        if (isArith(t, "-")) {
            return new Condition.Expr.BinaryOp(new Condition.Expr.Literal("0"),
                    Condition.Expr.ArithOperator.SUBTRACT, parseFactor());
        }
        return switch (t.type()) {
            case LPAREN -> {
                Condition.Expr inner = parseArithExpr();
                expect(TokenType.RPAREN);
                yield inner;
            }
            case PLACEHOLDER -> new Condition.Expr.Placeholder(t.text());
            case NUMBER, WORD -> new Condition.Expr.Literal(t.text());
            default -> throw new SyntaxException("Expected a value, found '" + t.text() + "'");
        };
    }

    // Paren disambiguation

    private record ParenSpan(boolean logical) {}

    private ParenSpan scanParen(int openIndex) {
        int depth = 0;
        boolean logical = false;
        for (int i = openIndex; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.LPAREN) {
                depth++;
            } else if (t.type() == TokenType.RPAREN) {
                depth--;
                if (depth == 0) {
                    return new ParenSpan(logical);
                }
            } else if (depth >= 1 && (t.type() == TokenType.COMPARE_OP || isWord(t, "AND") || isWord(t, "OR"))) {
                logical = true;
            }
        }
        throw new SyntaxException("Unbalanced parentheses");
    }

    // Token helpers

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private TokenType peekType() {
        Token t = peek();
        return t != null ? t.type() : null;
    }

    private Token advance() {
        if (pos >= tokens.size()) {
            throw new SyntaxException("Condition ended unexpectedly");
        }
        return tokens.get(pos++);
    }

    private void expect(TokenType type) {
        Token t = advance();
        if (t.type() != type) {
            throw new SyntaxException("Expected " + type + " but found '" + t.text() + "'");
        }
    }

    private static boolean isWord(Token t, String text) {
        return t != null && t.type() == TokenType.WORD && t.text().equalsIgnoreCase(text);
    }

    private static boolean isArith(Token t, String symbol) {
        return t != null && t.type() == TokenType.ARITH_OP && t.text().equals(symbol);
    }

    private Condition.Operator expectCompareOp() {
        Token t = advance();
        if (t.type() != TokenType.COMPARE_OP) {
            throw new SyntaxException("Expected a comparison operator, found '" + t.text() + "'");
        }
        return switch (t.text()) {
            case "==" -> Condition.Operator.EQUALS;
            case "!=" -> Condition.Operator.NOT_EQUALS;
            case "=~" -> Condition.Operator.EQUALS_CI;
            case "!~" -> Condition.Operator.NOT_EQUALS_CI;
            case ">" -> Condition.Operator.GREATER;
            case "<" -> Condition.Operator.LESS;
            case ">=" -> Condition.Operator.GREATER_OR_EQUAL;
            case "<=" -> Condition.Operator.LESS_OR_EQUAL;
            case "<>" -> Condition.Operator.CONTAINS;
            case "!<>" -> Condition.Operator.NOT_CONTAINS;
            case "|-" -> Condition.Operator.STARTS_WITH;
            case "-|" -> Condition.Operator.ENDS_WITH;
            default -> throw new SyntaxException("Unknown operator '" + t.text() + "'");
        };
    }

    // Lexing

    private static final String[] COMPARE_OPS = {
            "!<>", "==", "!=", "=~", "!~", ">=", "<=", "<>", "|-", "-|", ">", "<"
    };

    private enum TokenType { NUMBER, PLACEHOLDER, WORD, COMPARE_OP, ARITH_OP, LPAREN, RPAREN }

    private record Token(TokenType type, String text) {}

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
            } else if (c == '%') {
                int end = input.indexOf('%', i + 1);
                if (end < 0) {
                    throw new SyntaxException("Unterminated placeholder in: " + input);
                }
                tokens.add(new Token(TokenType.PLACEHOLDER, input.substring(i + 1, end)));
                i = end + 1;
            } else if (matchCompareOp(input, i) != null) {
                String op = matchCompareOp(input, i);
                tokens.add(new Token(TokenType.COMPARE_OP, op));
                i += op.length();
            } else if (c == '+' || c == '*' || c == '/' || c == '-') {
                tokens.add(new Token(TokenType.ARITH_OP, String.valueOf(c)));
                i++;
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, input.substring(start, i)));
            } else if (isWordChar(c)) {
                int start = i;
                while (i < len && isWordChar(input.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.WORD, input.substring(start, i)));
            } else {
                throw new SyntaxException("Unexpected character '" + c + "' in: " + input);
            }
        }
        return tokens;
    }

    private static String matchCompareOp(String input, int at) {
        for (String op : COMPARE_OPS) {
            if (input.regionMatches(at, op, 0, op.length())) {
                return op;
            }
        }
        return null;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == ':';
    }

    public static final class SyntaxException extends RuntimeException {
        public SyntaxException(String message) {
            super(message);
        }
    }
}