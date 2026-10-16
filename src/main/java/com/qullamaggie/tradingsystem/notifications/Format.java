package com.qullamaggie.tradingsystem.notifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number formatting for emails, Swedish style: space as thousands separator
 * and comma as decimal separator. A plain space is used rather than the
 * narrow no-break space Locale("sv") produces, so the text looks the same
 * everywhere and tests can compare against ordinary strings.
 */
final class Format {

    private Format() {}

    /** 1168.5 -> "1 168,50" */
    static String price(BigDecimal value) {
        return decimalFormat("#,##0.00").format(value);
    }

    /** 199949.92 -> "199 950" */
    static String whole(BigDecimal value) {
        return decimalFormat("#,##0").format(value);
    }

    static String whole(long value) {
        return whole(BigDecimal.valueOf(value));
    }

    /** 4.678 -> "4,7" */
    static String oneDecimal(BigDecimal value) {
        return decimalFormat("0.0").format(value);
    }

    /** A fraction as a percentage without trailing zeros: 0.01 -> "1", 0.005 -> "0,5" */
    static String percent(BigDecimal fraction) {
        return decimalFormat("0.#").format(fraction.multiply(BigDecimal.valueOf(100)));
    }

    /** A plain number without trailing zeros: 2 -> "2", 2.5 -> "2,5" */
    static String number(BigDecimal value) {
        return decimalFormat("#,##0.#").format(value);
    }

    /** Plain decimal for JSON in PowerShell commands: 138.4 -> "138.40" */
    static String json(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** DecimalFormat isn't thread-safe, so a new one per call rather than a shared constant. */
    private static DecimalFormat decimalFormat(String pattern) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }
}
