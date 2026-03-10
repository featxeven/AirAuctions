package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.util.PlaceholderUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public record ItemPriority(
        List<Condition> parsedConditions,
        String materialSelector,
        Integer amount,
        String displayName,
        List<String> lore,
        Integer customModelData,
        List<String> actions
) {
    public static ItemPriority fromSection(ConfigurationSection sec) {
        List<Condition> conditions = sec.getStringList("conditions").stream()
                .map(Condition::parse)
                .toList();

        return new ItemPriority(
                conditions,
                sec.getString("material"),
                sec.contains("amount") ? sec.getInt("amount") : null,
                sec.getString("display-name"),
                sec.getStringList("lore"),
                sec.get("custom-model-data") instanceof Integer i ? i : null,
                sec.getStringList("actions")
        );
    }

    public String material() {
        return materialSelector;
    }

    public boolean matches(Player player, Map<String, String> context) {
        for (Condition cond : parsedConditions) {
            if (!cond.test(player, context)) return false;
        }
        return true;
    }

    public record Condition(String left, String op, String right, boolean isStatic) {
        private static final String[] OPS = {">=", "<=", "==", "!=", "=~", "!~", ">", "<"};

        public static Condition parse(String raw) {
            for (String op : OPS) {
                int idx = raw.indexOf(" " + op + " ");
                if (idx != -1) {
                    String left = raw.substring(0, idx).trim();
                    String right = raw.substring(idx + op.length() + 2).trim();
                    if (right.startsWith("'") && right.endsWith("'")) right = right.substring(1, right.length() - 1);
                    return new Condition(left, op, right, false);
                }
            }
            return new Condition(raw.trim(), null, null, true);
        }

        public boolean test(Player p, Map<String, String> ctx) {
            String l = PlaceholderUtil.apply(p, left, ctx);
            if (isStatic) return Boolean.parseBoolean(l);

            String r = PlaceholderUtil.apply(p, right, ctx);
            return switch (op) {
                case "==" -> l.equals(r);
                case "!=" -> !l.equals(r);
                case "=~" -> l.equalsIgnoreCase(r);
                case "!~" -> !l.equalsIgnoreCase(r);
                case ">"  -> compare(l, r) > 0;
                case "<"  -> compare(l, r) < 0;
                case ">=" -> compare(l, r) >= 0;
                case "<=" -> compare(l, r) <= 0;
                default -> false;
            };
        }

        private double compare(String l, String r) {
            try { return Double.parseDouble(l) - Double.parseDouble(r); }
            catch (NumberFormatException e) { return l.compareTo(r); }
        }
    }
}