import transfer.AdminFacade;
import transfer.BankingFacade;
import transfer.TransferException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    private static final String MENU = """

            1) Accounts           2) Transfer A->B->C    3) Begin transfer (A only)
            4) Continue transfer  5) Inspect transfer    6) All transfers
            7) Sanction IBAN      8) Close beneficiary   9) Cancel transfer
            10) Recover unfinished                       0) Exit""";

    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "data");
        BankingFacade bank = new BankingFacade(dir);
        AdminFacade admin = new AdminFacade(bank);
        if (admin.accounts().isEmpty()) {
            admin.openAccount("Alice", "USD", new BigDecimal("5000"));
            admin.openAccount("Bob", "KZT", new BigDecimal("1000000"));
        }
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println(MENU);
            String choice = ask(in, ">");
            if (choice == null || choice.equals("0")) {
                return;
            }
            try {
                handle(choice, in, bank, admin);
            } catch (TransferException e) {
                System.out.println("FAILED " + e.getMessage());
                if (e.transfer != null) {
                    System.out.println(admin.inspect(e.transfer.id));
                }
            } catch (RuntimeException e) {
                System.out.println("ERROR " + e.getMessage());
            }
        }
    }

    private static void handle(String choice, Scanner in, BankingFacade bank, AdminFacade admin) {
        switch (choice) {
            case "1" -> admin.accounts().forEach(System.out::println);
            case "2", "3" -> {
                String from = ask(in, "From account:");
                String iban = ask(in, "To IBAN:");
                BigDecimal amount = new BigDecimal(ask(in, "Amount:"));
                String currency = ask(in, "Currency (USD/EUR/KZT):");
                String id = choice.equals("2") ? bank.transfer(from, iban, amount, currency).id : bank.begin(from, iban, amount, currency).id;
                System.out.println(admin.inspect(id));
            }
            case "4" -> System.out.println(admin.inspect(bank.complete(ask(in, "Transfer id:")).id));
            case "5" -> System.out.println(admin.inspect(ask(in, "Transfer id:")));
            case "6" -> admin.transfers().forEach(System.out::println);
            case "7" -> admin.sanction(ask(in, "IBAN:"));
            case "8" -> admin.closeBeneficiary(ask(in, "IBAN:"));
            case "9" -> System.out.println(admin.inspect(admin.cancel(ask(in, "Transfer id:")).id));
            case "10" -> admin.recover().forEach(t -> System.out.println(admin.inspect(t.id)));
            default -> System.out.println("Unknown option");
        }
    }

    private static String ask(Scanner in, String prompt) {
        System.out.print(prompt + " ");
        return in.hasNextLine() ? in.nextLine().trim() : null;
    }
}
