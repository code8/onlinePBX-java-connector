package info.code8.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by code8 on 12/11/15.
 */

public class PhoneFormatter {
    private static final Pattern RUS_FORMAT = Pattern.compile("(7|8)?(\\d{3})(\\d{3})(\\d{2})(\\d{2})");

    public static String toText(Long number) {
        String raw;
        if (number != null && (raw = number.toString()).length() > 6) {
            Matcher matcher;
            if ((matcher = RUS_FORMAT.matcher(raw)).matches()) {
                // russian phone number
                return "+7 (" + matcher.group(2) + ") " + matcher.group(3) + "-" + matcher.group(4) + "-" + matcher.group(5);
            } else {
                // unknown phone number
                return raw;
            }
        }

        return null;
    }

    public static Long toNumber(String text) {
        try {
            if (text != null && text.length() > 9) {
                return Long.valueOf(text.replaceAll("[^\\d]", ""));
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }
}
