package ledger;

import store.CsvFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class AccountLedger {

    private final CsvFile accounts;
    private final CsvFile holds;
    private final CurrencyExchange exchange = new CurrencyExchange();

    public AccountLedger(Path dir) {
        accounts = new CsvFile(dir.resolve("accounts.csv"));
        holds = new CsvFile(dir.resolve("holds.csv"));
    }

    public String openAccount(String owner, String currency, BigDecimal deposit) {
        if (owner.isBlank() || !exchange.supports(currency) || deposit.signum() < 0) {
            throw new IllegalArgumentException("Invalid account: " + owner + " " + currency + " " + deposit);
        }
        String id = "ACC" + (accounts.rows().size() + 1);
        accounts.upsert(id, owner, currency, deposit.setScale(2, RoundingMode.HALF_UP).toPlainString());
        return id;
    }

    public List<String> describeAccounts() {
        return accounts.rows().stream()
                .map(r -> r[0] + " " + r[1] + " " + r[2] + " balance=" + balance(r[0]) + " available=" + available(r[0]))
                .toList();
    }

    public boolean exists(String accountId) {
        return accounts.find(accountId).isPresent();
    }

    public boolean supports(String currency) {
        return exchange.supports(currency);
    }

    public BigDecimal balance(String accountId) {
        return new BigDecimal(account(accountId)[3]).subtract(sum(accountId, Hold.Status.CAPTURED));
    }

    public BigDecimal available(String accountId) {
        return balance(accountId).subtract(sum(accountId, Hold.Status.HELD));
    }

    public Hold placeHold(String reference, String accountId, BigDecimal amount, String currency) {
        Optional<Hold> existing = hold(reference);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal needed = exchange.convert(amount, currency, account(accountId)[2]);
        if (needed.compareTo(available(accountId)) > 0) {
            throw new IllegalStateException("insufficient funds on " + accountId);
        }
        Hold hold = new Hold(reference, accountId, needed, Hold.Status.HELD);
        save(hold);
        return hold;
    }

    public void capture(String reference) {
        move(reference, Hold.Status.CAPTURED);
    }

    public void release(String reference) {
        move(reference, Hold.Status.RELEASED);
    }

    public Optional<Hold> hold(String reference) {
        return holds.find(reference).map(r -> new Hold(r[0], r[1], new BigDecimal(r[2]), Hold.Status.valueOf(r[3])));
    }

    private void move(String reference, Hold.Status next) {
        Hold hold = hold(reference).orElseThrow(() -> new IllegalArgumentException("Unknown hold: " + reference));
        hold.moveTo(next);
        save(hold);
    }

    private void save(Hold h) {
        holds.upsert(h.id, h.accountId, h.amount.toPlainString(), h.status().name());
    }

    private String[] account(String id) {
        return accounts.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown account: " + id));
    }

    private BigDecimal sum(String accountId, Hold.Status status) {
        return holds.rows().stream()
                .filter(r -> r[1].equals(accountId) && r[3].equals(status.name()))
                .map(r -> new BigDecimal(r[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
