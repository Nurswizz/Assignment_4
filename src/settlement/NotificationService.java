package settlement;

public class NotificationService {

    public String compose(Settlement s) {
        return switch (s.status()) {
            case SETTLED -> "Your payment of " + s.amount + " " + s.currency + " to " + s.iban + " has been sent";
            case FAILED -> "Your payment to " + s.iban + " could not be delivered: " + s.reason();
            case PENDING -> "Your payment to " + s.iban + " is being processed";
        };
    }
}
