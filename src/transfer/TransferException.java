package transfer;

public class TransferException extends RuntimeException {

    public final Transfer transfer;

    TransferException(String message) {
        super(message);
        transfer = null;
    }

    TransferException(Transfer t) {
        super("Transfer " + t.id + " " + t.status + ": " + t.reason);
        transfer = t;
    }
}
