package settlement;

import store.CsvFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Optional;

public class SettlementConfirmer {

    private final CsvFile settlements;
    private final CsvFile closedBeneficiaries;
    private final NotificationService notifications = new NotificationService();

    public SettlementConfirmer(Path dir) {
        settlements = new CsvFile(dir.resolve("settlements.csv"));
        closedBeneficiaries = new CsvFile(dir.resolve("closed-beneficiaries.csv"));
    }

    public Settlement settle(String reference, String iban, BigDecimal amount, String currency) {
        Optional<Settlement> existing = settlement(reference);
        if (existing.isPresent()) {
            return existing.get();
        }
        Settlement s = new Settlement(reference, iban, amount, currency, Settlement.Status.PENDING, "");
        if (closedBeneficiaries.find(iban).isPresent()) {
            s.fail("beneficiary " + iban + " is closed");
        } else {
            s.confirm();
        }
        settlements.upsert(s.id, s.iban, s.amount.toPlainString(), s.currency, s.status().name(), s.reason());
        return s;
    }

    public Optional<Settlement> settlement(String reference) {
        return settlements.find(reference)
                .map(r -> new Settlement(r[0], r[1], new BigDecimal(r[2]), r[3], Settlement.Status.valueOf(r[4]), r[5]));
    }

    public Optional<String> notification(String reference) {
        return settlement(reference).map(notifications::compose);
    }

    public void closeBeneficiary(String iban) {
        closedBeneficiaries.upsert(iban);
    }
}
