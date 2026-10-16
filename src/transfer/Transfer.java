package transfer;

import java.math.BigDecimal;

public class Transfer {

    public enum Status {
        PENDING_A, PENDING_B, PENDING_C, COMPENSATING, COMPLETED, COMPENSATED, REJECTED;

        public boolean isFinal() {
            return this == COMPLETED || this == COMPENSATED || this == REJECTED;
        }
    }

    public final String id;
    public final String from;
    public final String iban;
    public final BigDecimal amount;
    public final String currency;
    Status status;
    String reason;

    Transfer(String id, String from, String iban, BigDecimal amount, String currency, Status status, String reason) {
        this.id = id;
        this.from = from;
        this.iban = iban;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
