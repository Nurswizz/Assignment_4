package ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountLedgerTest {

    @TempDir
    Path dir;

    private AccountLedger ledger;
    private String alice;

    @BeforeEach
    void setUp() {
        ledger = new AccountLedger(dir);
        alice = ledger.openAccount("Alice", "USD", new BigDecimal("1000"));
    }

    @Test
    void holdThenCapture() {
        ledger.placeHold("T1", alice, new BigDecimal("300"), "USD");
        assertEquals(new BigDecimal("1000.00"), ledger.balance(alice));
        assertEquals(new BigDecimal("700.00"), ledger.available(alice));

        ledger.capture("T1");
        assertEquals(new BigDecimal("700.00"), ledger.balance(alice));
    }

    @Test
    void releaseRestoresFundsAndIsFinal() {
        ledger.placeHold("T1", alice, new BigDecimal("300"), "USD");
        ledger.release("T1");

        assertEquals(new BigDecimal("1000.00"), ledger.available(alice));
        assertThrows(IllegalStateException.class, () -> ledger.capture("T1"));
    }

    @Test
    void holdIsConvertedToAccountCurrency() {
        assertEquals(new BigDecimal("2.00"), ledger.placeHold("T1", alice, new BigDecimal("1040"), "KZT").amount);
    }

    @Test
    void rejectsBadRequests() {
        assertThrows(IllegalStateException.class, () -> ledger.placeHold("T1", alice, new BigDecimal("1000.01"), "USD"));
        assertThrows(IllegalArgumentException.class, () -> ledger.placeHold("T2", alice, BigDecimal.ZERO, "USD"));
        assertThrows(IllegalArgumentException.class, () -> ledger.placeHold("T3", alice, BigDecimal.ONE, "GBP"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("Bob", "GBP", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> ledger.release("missing"));
    }

    @Test
    void stateSurvivesRestart() {
        ledger.placeHold("T1", alice, new BigDecimal("100"), "USD");

        AccountLedger reopened = new AccountLedger(dir);

        assertEquals(new BigDecimal("900.00"), reopened.available(alice));
        assertEquals(Hold.Status.HELD, reopened.hold("T1").orElseThrow().status());
        assertEquals(1, reopened.describeAccounts().size());
    }
}
