package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class FormatService {
    private final AirAuctions plugin;

    public FormatService(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public double round(double value, boolean allowDecimals) {
        if (!allowDecimals) return Math.floor(value);

        return new BigDecimal(String.valueOf(value))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public Double parseAmount(String input, String currencyId) {
        if (input == null || input.isBlank()) return null;

        var provider = plugin.economy().getProvider(currencyId);
        boolean allowShort = plugin.config().economyAllowFormatShortInCommand();

        String s = input.trim().toLowerCase(Locale.ROOT);

        try {
            Double result = null;

            if (allowShort) {
                List<String> suffixes = plugin.config().economyFormatShortSuffixes();
                for (int i = suffixes.size() - 1; i >= 0; i--) {
                    String suffix = suffixes.get(i).toLowerCase(Locale.ROOT);
                    if (!suffix.isEmpty() && s.endsWith(suffix)) {
                        String numberPart = s.substring(0, s.length() - suffix.length());
                        result = new BigDecimal(numberPart)
                                .multiply(BigDecimal.valueOf(Math.pow(1000, i)))
                                .doubleValue();
                        break;
                    }
                }
            }

            if (result == null) {
                result = Double.parseDouble(s);
            }

            if (!provider.hasDecimals() && result % 1 != 0) {
                return null;
            }

            return result;
        } catch (Exception ex) {
            return null;
        }
    }

    public String format(double amount, String currencyId) {
        var provider = plugin.economy().getProvider(currencyId);
        String fmt = plugin.config().economyNumberFormat().toUpperCase(Locale.ROOT);
        double abs = Math.abs(amount);

        String formattedNumber = switch (fmt) {
            case "SHORT" -> formatShort(abs, provider.hasDecimals());
            case "FORMATTED" -> formatComma(abs, provider.hasDecimals());
            default -> provider.hasDecimals() ?
                    BigDecimal.valueOf(abs).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() :
                    String.valueOf((long) abs);
        };

        return provider.getDisplay(formattedNumber);
    }

    private String formatShort(double abs, boolean allowDecimals) {
        List<String> suffixes = plugin.config().economyFormatShortSuffixes();
        int index = 0;

        if (abs < 1000) {
            if (!allowDecimals) return String.valueOf((long) Math.floor(abs));
            return BigDecimal.valueOf(abs).setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
        }

        while (abs >= 1000 && index < suffixes.size() - 1) {
            abs /= 1000.0;
            index++;
        }

        return BigDecimal.valueOf(abs).setScale(allowDecimals ? 2 : 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + suffixes.get(index);
    }

    public String formatShortPlaceholder(double amount, String currencyId) {
        var provider = plugin.economy().getProvider(currencyId);
        return provider.getDisplay(formatShort(Math.abs(amount), provider.hasDecimals()));
    }

    private String formatComma(double abs, boolean allowDecimals) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(true);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(allowDecimals ? 2 : 0);
        return nf.format(abs);
    }
}