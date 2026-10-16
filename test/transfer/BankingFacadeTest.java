package transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static compliance.Screening.Status.CLEARED;
import static compliance.Screening.Status.REJECTED;
import static compliance.Screening.Status.REVOKED;
import static ledger.Hold.Status.CAPTURED;
import static ledger.Hold.Status.HELD;
import static ledger.Hold.Status.RELEASED;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static settlement.Settlement.Status.FAILED;
import static settlement.Settlement.Status.SETTLED;
import static transfer.BankScenario.CLOSED_IBAN;
import static transfer.BankScenario.SAFE_IBAN;
import static transfer.BankScenario.SANCTIONED_IBAN;
import static transfer.Transfer.Status.COMPENSATED;
import static transfer.Transfer.Status.COMPLETED;
import static transfer.Transfer.Status.PENDING_B;

class BankingFacadeTest {

    @TempDir
    Path dir;

    private BankScenario scenario;
    private String alice;

    @BeforeEach
    void setUp() {
        scenario = new BankScenario(dir);
        alice = scenario.customerWith("1000", "USD");
    }

    @Test
    void happyPath() {
        Transfer t = scenario.sends("100", "USD").from(alice).to(SAFE_IBAN);

        scenario.expectThat(t).endedAs(COMPLETED).holdIs(CAPTURED).screeningIs(CLEARED).settlementIs(SETTLED);
        scenario.expectBalance(alice, "900");
    }

    @Test
    void bFailsAfterA_onlyHoldIsReleased() {
        scenario.recipientIsSanctioned(SANCTIONED_IBAN);

        Transfer t = scenario.sends("100", "USD").from(alice).to(SANCTIONED_IBAN);

        scenario.expectThat(t).endedAs(COMPENSATED).holdIs(RELEASED).screeningIs(REJECTED).settlementIs(null);
        scenario.expectAvailable(alice, "1000");
    }

    @Test
    void cFailsAfterAAndB_screeningRevokedThenHoldReleased() {
        scenario.beneficiaryIsClosed(CLOSED_IBAN);

        Transfer t = scenario.sends("100", "USD").from(alice).to(CLOSED_IBAN);

        scenario.expectThat(t).endedAs(COMPENSATED).holdIs(RELEASED).screeningIs(REVOKED).settlementIs(FAILED);
        scenario.expectAvailable(alice, "1000");
    }

    @Test
    void insufficientFundsRejectedBeforeAnythingCommits() {
        Transfer t = scenario.sends("5000", "USD").from(alice).to(SAFE_IBAN);

        scenario.expectThat(t).endedAs(Transfer.Status.REJECTED).holdIs(null).screeningIs(null);
        assertTrue(t.reason().startsWith("A:"));
    }

    @Test
    void invalidInputPersistsNothing() {
        BankingFacade bank = scenario.bank();

        assertNull(assertThrows(TransferException.class, () -> bank.transfer(alice, SAFE_IBAN, BigDecimal.ZERO, "USD")).transfer);
        assertThrows(TransferException.class, () -> bank.transfer(alice, SAFE_IBAN, BigDecimal.TEN, "GBP"));
        assertThrows(TransferException.class, () -> bank.transfer(alice, "bad-iban", BigDecimal.TEN, "USD"));
        assertThrows(TransferException.class, () -> bank.transfer("ACC404", SAFE_IBAN, BigDecimal.TEN, "USD"));
        assertTrue(bank.all().isEmpty());
    }

    @Test
    void restartAfterAContinuesFromDisk() {
        Transfer t = scenario.sends("100", "USD").from(alice).startedTo(SAFE_IBAN);
        scenario.expectThat(t).endedAs(PENDING_B).holdIs(HELD).screeningIs(null);

        new BankingFacade(dir).complete(t.id);

        scenario.expectThat(t).endedAs(COMPLETED).holdIs(CAPTURED).screeningIs(CLEARED).settlementIs(SETTLED);
    }

    @Test
    void restartThenCFailureCompensatesBoth() {
        Transfer t = scenario.sends("100", "USD").from(alice).startedTo(CLOSED_IBAN);
        scenario.beneficiaryIsClosed(CLOSED_IBAN);

        assertThrows(TransferException.class, () -> new BankingFacade(dir).complete(t.id));

        scenario.expectThat(t).endedAs(COMPENSATED).holdIs(RELEASED).screeningIs(REVOKED).settlementIs(FAILED);
    }
}
