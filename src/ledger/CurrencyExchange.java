package ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class CurrencyExchange {

    private static final Map<String, BigDecimal> KZT_PER_UNIT = Map.of(
            "KZT", BigDecimal.ONE,
            "USD", new BigDecimal("520"),
            "EUR", new BigDecimal("560"));

    public boolean supports(String currency) {
        return currency != null && KZT_PER_UNIT.containsKey(currency);
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (!supports(from) || !supports(to)) {
            throw new IllegalArgumentException("Unsupported currency: " + from + "/" + to);
        }
        return amount.multiply(KZT_PER_UNIT.get(from)).divide(KZT_PER_UNIT.get(to), 2, RoundingMode.HALF_UP);
    }
}
