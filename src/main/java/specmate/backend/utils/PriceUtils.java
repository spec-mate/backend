package specmate.backend.utils;

public class PriceUtils {
    public static Long safeParsePrice(Object price) {
        if (price == null) return 0L;
        if (price instanceof Number) return ((Number) price).longValue();
        if (price instanceof String) {
            String str = ((String) price).replaceAll("[^0-9]", "");
            return str.isEmpty() ? 0L : Long.parseLong(str);
        }
        return 0L;
    }
}
