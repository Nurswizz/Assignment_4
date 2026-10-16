package compliance;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class FraudDetector {

    static final int MAX_TRANSFERS_PER_DAY = 3;

    public Optional<String> check(Screening candidate, List<Screening> history) {
        long recent = history.stream()
                .filter(s -> s.accountId.equals(candidate.accountId) && s.status() == Screening.Status.CLEARED)
                .filter(s -> s.createdAt.isAfter(candidate.createdAt.minus(Duration.ofDays(1))))
                .count();
        return recent >= MAX_TRANSFERS_PER_DAY
                ? Optional.of("fraud: more than " + MAX_TRANSFERS_PER_DAY + " transfers in 24h")
                : Optional.empty();
    }
}
