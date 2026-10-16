package transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static compliance.Screening.Status.REVOKED;
import static ledger.Hold.Status.RELEASED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static transfer.BankScenario.SAFE_IBAN;
import static transfer.Transfer.Status.COMPENSATED;
import static transfer.Transfer.Status.COMPLETED;
import static transfer.Transfer.Status.PENDING_C;

class AdminFacadeTest {

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
    void cancelAfterBRevokesAndReleases() {
        Transfer t = scenario.sends("100", "USD").from(alice).startedTo(SAFE_IBAN);
        BankingFacade bank = scenario.bank();
        bank.compliance.screen(t.id, alice, SAFE_IBAN);
        Transfer onDisk = bank.load(t.id);
        onDisk.status = PENDING_C;
        bank.save(onDisk);

        scenario.admin().cancel(t.id);

        scenario.expectThat(t).endedAs(COMPENSATED).holdIs(RELEASED).screeningIs(REVOKED);
    }

    @Test
    void cannotCancelFinishedTransfer() {
        Transfer t = scenario.sends("100", "USD").from(alice).to(SAFE_IBAN);

        assertThrows(IllegalStateException.class, () -> scenario.admin().cancel(t.id));
    }

    @Test
    void recoverFinishesInterruptedWork() {
        Transfer pending = scenario.sends("100", "USD").from(alice).startedTo(SAFE_IBAN);
        Transfer crashed = scenario.sends("100", "USD").from(alice).startedTo(SAFE_IBAN);
        BankingFacade bank = scenario.bank();
        Transfer onDisk = bank.load(crashed.id);
        bank.startCompensation(onDisk, "crashed while compensating");
        bank.save(onDisk);

        assertEquals(2, scenario.admin().recover().size());

        scenario.expectThat(pending).endedAs(COMPLETED);
        scenario.expectThat(crashed).endedAs(COMPENSATED).holdIs(RELEASED);
        scenario.expectBalance(alice, "900");
    }

    @Test
    void inspectShowsEverySubsystem() {
        Transfer t = scenario.sends("100", "USD").from(alice).to(SAFE_IBAN);
        AdminFacade admin = scenario.admin();

        assertTrue(admin.transfers().getFirst().contains("hold=CAPTURED screening=CLEARED settlement=SETTLED"));
        assertTrue(admin.inspect(t.id).contains("has been sent"));
        assertTrue(admin.accounts().getFirst().contains("balance=900.00"));
    }
}
