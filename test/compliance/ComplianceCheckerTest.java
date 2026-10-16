package compliance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplianceCheckerTest {

    private static final String IBAN = "DE89370400440532013000";

    @TempDir
    Path dir;

    private ComplianceChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ComplianceChecker(dir);
    }

    @Test
    void clearsThenRevokes() {
        assertEquals(Screening.Status.CLEARED, checker.screen("T1", "ACC1", IBAN).status());

        checker.revoke("T1");

        assertEquals(Screening.Status.REVOKED, checker.screening("T1").orElseThrow().status());
    }

    @Test
    void rejectsSanctionedRecipient() {
        checker.sanction(IBAN);

        assertEquals(Screening.Status.REJECTED, checker.screen("T1", "ACC1", IBAN).status());
        assertThrows(IllegalStateException.class, () -> checker.revoke("T1"));
    }

    @Test
    void rejectsFourthTransferInADay() {
        checker.screen("T1", "ACC1", IBAN);
        checker.screen("T2", "ACC1", IBAN);
        checker.screen("T3", "ACC1", IBAN);

        assertEquals(Screening.Status.REJECTED, checker.screen("T4", "ACC1", IBAN).status());
        assertEquals(Screening.Status.CLEARED, checker.screen("T5", "ACC2", IBAN).status());
    }

    @Test
    void fraudDetectorIgnoresOldTransfers() {
        Instant now = Instant.now();
        List<Screening> history = List.of(cleared(now.minus(Duration.ofHours(30))), cleared(now.minus(Duration.ofHours(40))),
                cleared(now.minus(Duration.ofHours(50))));

        assertTrue(new FraudDetector().check(cleared(now), history).isEmpty());
    }

    @Test
    void screeningIsOncePerReferenceAndSurvivesRestart() {
        checker.screen("T1", "ACC1", IBAN);
        checker.sanction(IBAN);

        assertEquals(Screening.Status.CLEARED, new ComplianceChecker(dir).screen("T1", "ACC1", IBAN).status());
        assertThrows(IllegalArgumentException.class, () -> checker.revoke("missing"));
    }

    private static Screening cleared(Instant at) {
        return new Screening("S" + at, "ACC1", IBAN, at, Screening.Status.CLEARED, "");
    }
}
