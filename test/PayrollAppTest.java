import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PayrollAppTest {
    public static void main(String[] args) throws Exception {
        PayrollApp.Employee employee = new PayrollApp.Employee(
                "E-101", "Asha Rao", "Engineering, R&D", "asha@example.com", "Active",
                new BigDecimal("44000"), new BigDecimal("6600"), new BigDecimal("3500"),
                new BigDecimal("5"), new BigDecimal("250"), 20, 22);
        PayrollApp.PayBreakdown pay = employee.payroll();
        check("40000.00", pay.proratedBasic().toPlainString(), "attendance-adjusted basic");
        check("6000.00", pay.proratedAllowances().toPlainString(), "attendance-adjusted allowance");
        check("1250.00", pay.overtimePay().toPlainString(), "overtime");
        check("47250.00", pay.grossPay().toPlainString(), "gross pay");
        check("43750.00", pay.netPay().toPlainString(), "net pay");

        Path temp = Files.createTempFile("payroll-roundtrip-", ".csv");
        try {
            PayrollApp.CsvStore.write(temp, List.of(employee));
            List<PayrollApp.Employee> rows = PayrollApp.CsvStore.read(temp);
            check(1, rows.size(), "CSV row count");
            check("Engineering, R&D", rows.get(0).department(), "CSV comma escaping");
            check("Asha Rao", rows.get(0).name(), "CSV employee name");
            check("43750.00", rows.get(0).payroll().netPay().toPlainString(), "CSV pay round-trip");
        } finally {
            Files.deleteIfExists(temp);
        }
        System.out.println("All payroll calculation and CSV round-trip tests passed.");
    }

    private static void check(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
