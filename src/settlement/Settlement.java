package settlement;

import java.math.BigDecimal;

public class Settlement {

    public enum Status { PENDING, SETTLED, FAILED }

    public final String id;
    public final String iban;
    public final BigDecimal amount;
    public final String currency;
    private Status status;
    private String reason;

    public Settlement(String id, String iban, BigDecimal amount, String currency, Status status, String reason) {
        this.id = id;
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

    void confirm() {
        moveTo(Status.SETTLED);
    }

    void fail(String reason) {
        moveTo(Status.FAILED);
        this.reason = reason;
    }

    private void moveTo(Status next) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Settlement " + id + " is " + status + " and cannot become " + next);
        }
        status = next;
    }
}
