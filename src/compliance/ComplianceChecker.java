package compliance;

import store.CsvFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class ComplianceChecker {

    private final CsvFile screenings;
    private final CsvFile sanctions;
    private final FraudDetector fraudDetector = new FraudDetector();

    public ComplianceChecker(Path dir) {
        screenings = new CsvFile(dir.resolve("screenings.csv"));
        sanctions = new CsvFile(dir.resolve("sanctions.csv"));
    }

    public Screening screen(String reference, String accountId, String iban) {
        Optional<Screening> existing = screening(reference);
        if (existing.isPresent()) {
            return existing.get();
        }
        Screening s = new Screening(reference, accountId, iban, Instant.now(), Screening.Status.PENDING, "");
        if (sanctions.find(iban).isPresent()) {
            s.reject("recipient " + iban + " is sanctioned");
        } else {
            fraudDetector.check(s, screenings.rows().stream().map(ComplianceChecker::parse).toList())
                    .ifPresentOrElse(s::reject, s::clear);
        }
        save(s);
        return s;
    }

    public void revoke(String reference) {
        Screening s = screening(reference).orElseThrow(() -> new IllegalArgumentException("Unknown screening: " + reference));
        s.revoke();
        save(s);
    }

    public Optional<Screening> screening(String reference) {
        return screenings.find(reference).map(ComplianceChecker::parse);
    }

    public void sanction(String iban) {
        sanctions.upsert(iban);
    }

    private void save(Screening s) {
        screenings.upsert(s.id, s.accountId, s.iban, s.createdAt.toString(), s.status().name(), s.reason());
    }

    private static Screening parse(String[] r) {
        return new Screening(r[0], r[1], r[2], Instant.parse(r[3]), Screening.Status.valueOf(r[4]), r[5]);
    }
}
