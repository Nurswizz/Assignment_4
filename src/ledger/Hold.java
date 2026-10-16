package ledger;

import java.math.BigDecimal;

public class Hold {

    public enum Status { HELD, CAPTURED, RELEASED }

    public final String id;
    public final String accountId;
    public final BigDecimal amount;
    private Status status;

    public Hold(String id, String accountId, BigDecimal amount, Status status) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.status = status;
    }

    public Status status() {
        return status;
    }

    void moveTo(Status next) {
        if (status != Status.HELD) {
            throw new IllegalStateException("Hold " + id + " is " + status + " and cannot become " + next);
        }
        status = next;
    }
}
