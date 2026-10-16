package transfer;

import compliance.Screening;
import ledger.Hold;
import settlement.Settlement;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankScenario {

    static final String SAFE_IBAN = "DE89370400440532013000";
    static final String SANCTIONED_IBAN = "GB29NWBK60161331926819";
    static final String CLOSED_IBAN = "FR1420041010050500013M02606";

    private final Path dir;

    BankScenario(Path dir) {
        this.dir = dir;
    }

    BankingFacade bank() {
        return new BankingFacade(dir);
    }

    AdminFacade admin() {
        return new AdminFacade(bank());
    }

    String customerWith(String amount, String currency) {
        return admin().openAccount("Customer", currency, new BigDecimal(amount));
    }

    void recipientIsSanctioned(String iban) {
        admin().sanction(iban);
    }

    void beneficiaryIsClosed(String iban) {
        admin().closeBeneficiary(iban);
    }

    Payment sends(String amount, String currency) {
        return new Payment(new BigDecimal(amount), currency);
    }

    Expectation expectThat(Transfer t) {
        return new Expectation(bank(), t.id);
    }

    void expectAvailable(String account, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(bank().ledger.available(account)));
    }

    void expectBalance(String account, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(bank().ledger.balance(account)));
    }

    class Payment {

        private final BigDecimal amount;
        private final String currency;
        private String from;

        Payment(BigDecimal amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        Payment from(String account) {
            from = account;
            return this;
        }

        Transfer to(String iban) {
            try {
                return bank().transfer(from, iban, amount, currency);
            } catch (TransferException e) {
                return e.transfer;
            }
        }

        Transfer startedTo(String iban) {
            return bank().begin(from, iban, amount, currency);
        }
    }

    record Expectation(BankingFacade bank, String id) {

        Expectation endedAs(Transfer.Status status) {
            assertEquals(status, bank.load(id).status);
            return this;
        }

        Expectation holdIs(Hold.Status status) {
            assertEquals(status, bank.ledger.hold(id).map(Hold::status).orElse(null));
            return this;
        }

        Expectation screeningIs(Screening.Status status) {
            assertEquals(status, bank.compliance.screening(id).map(Screening::status).orElse(null));
            return this;
        }

        Expectation settlementIs(Settlement.Status status) {
            assertEquals(status, bank.settlement.settlement(id).map(Settlement::status).orElse(null));
            return this;
        }
    }
}
