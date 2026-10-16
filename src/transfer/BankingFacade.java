package transfer;

import compliance.ComplianceChecker;
import compliance.Screening;
import ledger.AccountLedger;
import ledger.Hold;
import settlement.Settlement;
import settlement.SettlementConfirmer;
import store.CsvFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class BankingFacade {

    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}");

    final AccountLedger ledger;
    final ComplianceChecker compliance;
    final SettlementConfirmer settlement;
    private final CsvFile journal;

    public BankingFacade(Path dir) {
        ledger = new AccountLedger(dir);
        compliance = new ComplianceChecker(dir);
        settlement = new SettlementConfirmer(dir);
        journal = new CsvFile(dir.resolve("transfers.csv"));
    }

    public Transfer transfer(String from, String iban, BigDecimal amount, String currency) {
        return complete(begin(from, iban, amount, currency).id);
    }

    public Transfer begin(String from, String iban, BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new TransferException("Invalid amount: " + amount);
        }
        if (!ledger.supports(currency)) {
            throw new TransferException("Unsupported currency: " + currency);
        }
        if (iban == null || !IBAN.matcher(iban).matches()) {
            throw new TransferException("Invalid IBAN: " + iban);
        }
        if (from == null || !ledger.exists(from)) {
            throw new TransferException("Unknown account: " + from);
        }
        String id = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Transfer t = new Transfer(id, from, iban, amount, currency, Transfer.Status.PENDING_A, "");
        save(t);
        step(t);
        if (t.status == Transfer.Status.REJECTED) {
            throw new TransferException(t);
        }
        return t;
    }

    public Transfer complete(String id) {
        Transfer t = drive(load(id));
        if (t.status != Transfer.Status.COMPLETED) {
            throw new TransferException(t);
        }
        return t;
    }

    Transfer drive(Transfer t) {
        while (!t.status.isFinal()) {
            step(t);
        }
        return t;
    }

    void startCompensation(Transfer t, String reason) {
        t.status = Transfer.Status.COMPENSATING;
        t.reason = reason;
    }

    private void step(Transfer t) {
        switch (t.status) {
            case PENDING_A -> reserve(t);
            case PENDING_B -> screen(t);
            case PENDING_C -> settle(t);
            case COMPENSATING -> compensate(t);
            default -> { }
        }
        save(t);
    }

    private void reserve(Transfer t) {
        try {
            ledger.placeHold(t.id, t.from, t.amount, t.currency);
            t.status = Transfer.Status.PENDING_B;
        } catch (IllegalStateException e) {
            t.status = Transfer.Status.REJECTED;
            t.reason = "A: " + e.getMessage();
        }
    }

    private void screen(Transfer t) {
        Screening s = compliance.screen(t.id, t.from, t.iban);
        if (s.status() == Screening.Status.CLEARED) {
            t.status = Transfer.Status.PENDING_C;
        } else {
            startCompensation(t, "B: " + s.reason());
        }
    }

    private void settle(Transfer t) {
        Settlement s = settlement.settle(t.id, t.iban, t.amount, t.currency);
        if (s.status() != Settlement.Status.SETTLED) {
            startCompensation(t, "C: " + s.reason());
            return;
        }
        ledger.hold(t.id).filter(h -> h.status() == Hold.Status.HELD).ifPresent(h -> ledger.capture(t.id));
        t.status = Transfer.Status.COMPLETED;
    }

    private void compensate(Transfer t) {
        compliance.screening(t.id).filter(s -> s.status() == Screening.Status.CLEARED).ifPresent(s -> compliance.revoke(t.id));
        ledger.hold(t.id).filter(h -> h.status() == Hold.Status.HELD).ifPresent(h -> ledger.release(t.id));
        t.status = Transfer.Status.COMPENSATED;
    }

    void save(Transfer t) {
        journal.upsert(t.id, t.from, t.iban, t.amount.toPlainString(), t.currency, t.status.name(), t.reason);
    }

    Transfer load(String id) {
        return journal.find(id).map(BankingFacade::parse).orElseThrow(() -> new IllegalArgumentException("Unknown transfer: " + id));
    }

    List<Transfer> all() {
        return journal.rows().stream().map(BankingFacade::parse).toList();
    }

    private static Transfer parse(String[] r) {
        return new Transfer(r[0], r[1], r[2], new BigDecimal(r[3]), r[4], Transfer.Status.valueOf(r[5]), r[6]);
    }
}
