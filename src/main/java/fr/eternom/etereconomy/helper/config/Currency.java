package fr.eternom.etereconomy.helper.config;

/**
 * Turns a raw amount into a well-behaved currency value: rounded to a fixed number of decimal
 * places, and rendered with grouped digits plus the singular/plural currency name. Shared by the
 * economy and bank managers so both display and round money exactly the same way.
 */
public final class Currency {

    private Currency() {
    }

    public static double round(double value, int fractionalDigits) {
        double factor = Math.pow(10, fractionalDigits);
        return Math.round(value * factor) / factor;
    }

    public static String format(double value, int fractionalDigits, String nameSingular, String namePlural) {
        String formatted = String.format("%,." + fractionalDigits + "f", value);
        return formatted + " " + (Math.abs(value) == 1.0 ? nameSingular : namePlural);
    }
}
