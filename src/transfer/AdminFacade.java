package transfer;

import settlement.Settlement;

import java.math.BigDecimal;
import java.util.List;

public class AdminFacade {

    private final BankingFacade bank;

    public AdminFacade(BankingFacade bank) {
        this.bank = bank;
    }

    public String openAccount(String owner, String currency, BigDecimal deposit) {
        return bank.ledger.openAccount(owner, currency, deposit);
    }

    public List<String> accounts() {
        return bank.ledger.describeAccounts();
    }

    public String inspect(String id) {
        Transfer t = bank.load(id);
        return t.id + " " + t.from + " -> " + t.iban + " " + t.amount + " " + t.currency + " [" + t.status + "]"
                + " hold=" + bank.ledger.hold(id).map(h -> h.status().name()).orElse("-")
                + " screening=" + bank.compliance.screening(id).map(s -> s.status().name()).orElse("-")
                + " settlement=" + bank.settlement.settlement(id).map(s -> s.status().name()).orElse("-")
                + (t.reason.isEmpty() ? "" : "\n  reason: " + t.reason)
                + bank.settlement.notification(id).map(n -> "\n  notification: " + n).orElse("");
    }

    public List<String> transfers() {
        return bank.all().stream().map(t -> inspect(t.id)).toList();
    }

    public void sanction(String iban) {
        bank.compliance.sanction(iban);
    }

    public void closeBeneficiary(String iban) {
        bank.settlement.closeBeneficiary(iban);
    }

    public Transfer cancel(String id) {
        Transfer t = bank.load(id);
        boolean settled = bank.settlement.settlement(id).filter(s -> s.status() == Settlement.Status.SETTLED).isPresent();
        if (t.status.isFinal() || settled) {
            throw new IllegalStateException("Transfer " + id + " can no longer be cancelled");
        }
        bank.startCompensation(t, "cancelled by admin");
        bank.save(t);
        return bank.drive(t);
    }

    public List<Transfer> recover() {
        return bank.all().stream().filter(t -> !t.status.isFinal()).map(bank::drive).toList();
    }
}
