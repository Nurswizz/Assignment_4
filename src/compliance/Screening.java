package compliance;

import java.time.Instant;

public class Screening {

    public enum Status { PENDING, CLEARED, REJECTED, REVOKED }

    public final String id;
    public final String accountId;
    public final String iban;
    public final Instant createdAt;
    private Status status;
    private String reason;

    public Screening(String id, String accountId, String iban, Instant createdAt, Status status, String reason) {
        this.id = id;
        this.accountId = accountId;
        this.iban = iban;
        this.createdAt = createdAt;
        this.status = status;
        this.reason = reason;
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    void clear() {
        moveTo(Status.PENDING, Status.CLEARED);
    }

    void reject(String reason) {
        moveTo(Status.PENDING, Status.REJECTED);
        this.reason = reason;
    }

    void revoke() {
        moveTo(Status.CLEARED, Status.REVOKED);
    }

    private void moveTo(Status expected, Status next) {
        if (status != expected) {
            throw new IllegalStateException("Screening " + id + " is " + status + " and cannot become " + next);
        }
        status = next;
    }
}
