package settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementConfirmerTest {

    private static final String IBAN = "FR1420041010050500013M02606";

    @TempDir
    Path dir;

    private SettlementConfirmer confirmer;

    @BeforeEach
    void setUp() {
        confirmer = new SettlementConfirmer(dir);
    }

    @Test
    void settlesOpenBeneficiary() {
        assertEquals(Settlement.Status.SETTLED, confirmer.settle("T1", IBAN, BigDecimal.TEN, "EUR").status());
        assertTrue(confirmer.notification("T1").orElseThrow().contains("has been sent"));
    }

    @Test
    void failsClosedBeneficiary() {
        confirmer.closeBeneficiary(IBAN);

        assertEquals(Settlement.Status.FAILED, confirmer.settle("T1", IBAN, BigDecimal.TEN, "EUR").status());
        assertTrue(confirmer.notification("T1").orElseThrow().contains("could not be delivered"));
    }

    @Test
    void settlementIsOncePerReferenceAndSurvivesRestart() {
        confirmer.settle("T1", IBAN, BigDecimal.TEN, "EUR");
        confirmer.closeBeneficiary(IBAN);

        assertEquals(Settlement.Status.SETTLED, new SettlementConfirmer(dir).settle("T1", IBAN, BigDecimal.TEN, "EUR").status());
    }

    @Test
    void finishedSettlementCannotChange() {
        Settlement s = new Settlement("T1", IBAN, BigDecimal.TEN, "EUR", Settlement.Status.PENDING, "");
        assertTrue(new NotificationService().compose(s).contains("being processed"));

        s.confirm();

        assertThrows(IllegalStateException.class, () -> s.fail("late"));
    }
}
