import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/** Employee Payroll Management System — Java Swing reference implementation. */
public class PayrollApp {
    private static final Path DATA_FILE = Paths.get("data", "employees.csv");
    private static final String[] COLUMNS = {"Employee ID", "Name", "Department", "Status", "Net Pay"};
    private final TreeMap<String, Employee> employees = new TreeMap<>();
    private final HashMap<String, Employee> employeeIndex = new HashMap<>();
    private final ArrayList<Employee> employeeList = new ArrayList<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JTextField searchField = new JTextField(22);
    private final JLabel summaryLabel = new JLabel(" ");
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private String selectedId;

    private PayrollApp() {
        loadEmployees();
        refreshTable();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new PayrollApp().show();
        });
    }

    private void show() {
        JFrame frame = new JFrame("Employee Payroll Management System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 700));
        frame.setSize(1160, 790);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(buildContent());
        frame.setVisible(true);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 18, 14, 18));
        root.setBackground(new Color(246, 248, 251));

        JLabel title = new JLabel("Employee Payroll Management System");
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        JLabel subtitle = new JLabel("Manage employee records, attendance and monthly payslips");
        subtitle.setForeground(new Color(90, 100, 115));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.SOUTH);
        root.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(buildForm(), BorderLayout.WEST);
        center.add(buildDirectory(), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        summaryLabel.setBorder(new EmptyBorder(8, 2, 0, 2));
        summaryLabel.setForeground(new Color(58, 70, 88));
        root.add(summaryLabel, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setPreferredSize(new Dimension(340, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 232)),
                new EmptyBorder(14, 14, 14, 14)));
        panel.setBackground(Color.WHITE);

        JLabel formTitle = new JLabel("Employee details");
        formTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(formTitle, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        String[][] specs = {
                {"Employee ID", "employeeId"}, {"Full name", "name"}, {"Department", "department"},
                {"Email", "email"}, {"Basic monthly pay", "basic"}, {"Monthly allowances", "allowances"},
                {"Monthly deductions", "deductions"}, {"Overtime hours", "overtimeHours"},
                {"Overtime rate / hour", "overtimeRate"}, {"Paid days", "paidDays"},
                {"Working days", "workingDays"}, {"Status (Active/Inactive)", "status"}
        };
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 2, 0);
        int row = 0;
        for (String[] spec : specs) {
            c.gridy = row++;
            JLabel label = new JLabel(spec[0]);
            label.setFont(new Font("SansSerif", Font.PLAIN, 12));
            grid.add(label, c);
            c.gridy = row++;
            JTextField input = new JTextField();
            input.setFont(new Font("SansSerif", Font.PLAIN, 13));
            fields.put(spec[1], input);
            grid.add(input, c);
        }
        JScrollPane formScroll = new JScrollPane(grid);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(14);
        panel.add(formScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0, 2, 7, 7));
        buttons.setOpaque(false);
        addButton(buttons, "Add employee", this::addEmployee, true);
        addButton(buttons, "Update selected", this::updateEmployee, false);
        addButton(buttons, "Clear form", this::clearForm, false);
        addButton(buttons, "Delete selected", this::deleteEmployee, false);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildDirectory() {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 232)),
                new EmptyBorder(14, 14, 14, 14)));
        panel.setBackground(Color.WHITE);

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        JLabel label = new JLabel("Employee directory");
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        search.setOpaque(false);
        search.add(new JLabel("Search"));
        search.add(searchField);
        searchField.setToolTipText("Filter by ID, name, department or status");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshTable(); }
        });
        toolbar.add(label, BorderLayout.WEST);
        toolbar.add(search, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        table.setRowHeight(30);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) loadSelectedIntoForm();
        });
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(232, 235, 240)));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        addButton(actions, "Generate payslip", this::showPayslip, true);
        addButton(actions, "Toggle status", this::toggleStatus, false);
        addButton(actions, "Export CSV", this::exportCsv, false);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void addButton(JPanel panel, String text, Runnable action, boolean primary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        if (primary) {
            button.setBackground(new Color(39, 88, 164));
            button.setForeground(Color.WHITE);
            button.setBorder(BorderFactory.createEmptyBorder(8, 11, 8, 11));
        }
        button.addActionListener(e -> action.run());
        panel.add(button);
    }

    private Employee readForm() {
        String id = value("employeeId");
        String name = value("name");
        String department = value("department");
        String email = value("email");
        String status = value("status").isBlank() ? "Active" : value("status");
        if (id.isBlank() || name.isBlank() || department.isBlank())
            throw new IllegalArgumentException("Employee ID, full name, and department are required.");
        if (!status.equalsIgnoreCase("Active") && !status.equalsIgnoreCase("Inactive"))
            throw new IllegalArgumentException("Status must be Active or Inactive.");
        if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Enter a valid email address or leave it blank.");
        BigDecimal basic = money("basic", "Basic monthly pay");
        BigDecimal allowances = money("allowances", "Monthly allowances");
        BigDecimal deductions = money("deductions", "Monthly deductions");
        BigDecimal overtimeRate = money("overtimeRate", "Overtime rate");
        BigDecimal overtimeHours = decimal("overtimeHours", "Overtime hours");
        int paidDays = integer("paidDays", "Paid days");
        int workingDays = integer("workingDays", "Working days");
        if (basic.signum() < 0 || allowances.signum() < 0 || deductions.signum() < 0
                || overtimeRate.signum() < 0 || overtimeHours.signum() < 0)
            throw new IllegalArgumentException("Pay, deductions, and overtime values cannot be negative.");
        if (workingDays < 1 || paidDays < 0 || paidDays > workingDays)
            throw new IllegalArgumentException("Attendance must satisfy 0 ≤ paid days ≤ working days, with at least 1 working day.");
        Employee employee = new Employee(id, name, department, email, status, basic, allowances,
                deductions, overtimeHours, overtimeRate, paidDays, workingDays);
        if (employee.payroll().netPay().signum() < 0)
            throw new IllegalArgumentException("Deductions exceed gross pay. Review the entered amounts.");
        return employee;
    }

    private String value(String key) { return fields.get(key).getText().trim(); }
    private BigDecimal money(String key, String label) { return decimal(key, label); }
    private BigDecimal decimal(String key, String label) {
        try { return new BigDecimal(value(key)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " must be a valid number."); }
    }
    private int integer(String key, String label) {
        try { return Integer.parseInt(value(key)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " must be a whole number."); }
    }

    private void addEmployee() {
        try {
            Employee employee = readForm();
            if (employeeIndex.containsKey(employee.id())) throw new IllegalArgumentException("That employee ID already exists.");
            putEmployee(employee);
            persist();
            refreshTable();
            clearForm();
            message("Employee added successfully.", "Saved");
        } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
    }

    private void updateEmployee() {
        if (selectedId == null) { error("Select an employee from the directory first."); return; }
        try {
            Employee replacement = readForm();
            Employee collision = employeeIndex.get(replacement.id());
            if (collision != null && !replacement.id().equals(selectedId))
                throw new IllegalArgumentException("That employee ID already exists.");
            employees.remove(selectedId);
            employeeIndex.remove(selectedId);
            putEmployee(replacement);
            selectedId = replacement.id();
            persist(); refreshTable();
            message("Employee record updated.", "Saved");
        } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
    }

    private void deleteEmployee() {
        if (selectedId == null) { error("Select an employee from the directory first."); return; }
        Employee employee = employeeIndex.get(selectedId);
        if (employee == null) return;
        int answer = JOptionPane.showConfirmDialog(null,
                "Delete " + employee.name() + " (" + employee.id() + ")? This removes the local employee record.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            employees.remove(selectedId); employeeIndex.remove(selectedId); selectedId = null;
            persist(); refreshTable(); clearForm();
        }
    }

    private void toggleStatus() {
        Employee current = selectedEmployee();
        if (current == null) { error("Select an employee from the directory first."); return; }
        Employee changed = current.withStatus(current.status().equals("Active") ? "Inactive" : "Active");
        putEmployee(changed); selectedId = changed.id(); persist(); refreshTable();
    }

    private void loadSelectedIntoForm() {
        Employee employee = selectedEmployee();
        if (employee == null) return;
        selectedId = employee.id();
        set("employeeId", employee.id()); set("name", employee.name());
        set("department", employee.department()); set("email", employee.email());
        set("basic", employee.basic().toPlainString()); set("allowances", employee.allowances().toPlainString());
        set("deductions", employee.deductions().toPlainString());
        set("overtimeHours", employee.overtimeHours().stripTrailingZeros().toPlainString());
        set("overtimeRate", employee.overtimeRate().toPlainString());
        set("paidDays", Integer.toString(employee.paidDays()));
        set("workingDays", Integer.toString(employee.workingDays())); set("status", employee.status());
    }

    private Employee selectedEmployee() {
        int row = table.getSelectedRow();
        if (row < 0) return selectedId == null ? null : employeeIndex.get(selectedId);
        String id = (String) table.getValueAt(table.convertRowIndexToModel(row), 0);
        return employeeIndex.get(id);
    }

    private void clearForm() {
        for (JTextField field : fields.values()) field.setText("");
        set("status", "Active"); set("paidDays", "22"); set("workingDays", "22");
        selectedId = null; table.clearSelection();
    }

    private void set(String key, String value) { fields.get(key).setText(value); }

    private void showPayslip() {
        Employee employee = selectedEmployee();
        if (employee == null) { error("Select an employee from the directory first."); return; }
        String slip = payslip(employee);
        JTextArea text = new JTextArea(slip, 22, 52);
        text.setEditable(false); text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int option = JOptionPane.showOptionDialog(null, new JScrollPane(text), "Payslip — " + employee.name(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[]{"Save as text…", "Close"}, "Close");
        if (option == 0) savePayslip(slip, employee);
    }

    private String payslip(Employee e) {
        PayBreakdown p = e.payroll();
        return "ITM SKILLS UNIVERSITY — EMPLOYEE PAYSLIP\n" + "=".repeat(54) + "\n"
                + "Pay period: " + LocalDate.now().getMonth() + " " + LocalDate.now().getYear() + "\n"
                + "Employee:   " + e.name() + " (" + e.id() + ")\n"
                + "Department: " + e.department() + "\nStatus:     " + e.status() + "\n"
                + "Attendance: " + e.paidDays() + " / " + e.workingDays() + " days\n\n"
                + "EARNINGS\n" + "-".repeat(54) + "\n"
                + line("Prorated basic pay", p.proratedBasic())
                + line("Prorated allowances", p.proratedAllowances())
                + line("Overtime (" + e.overtimeHours().stripTrailingZeros().toPlainString() + " hours)", p.overtimePay())
                + "\n" + line("Gross pay", p.grossPay())
                + line("Deductions", p.deductions())
                + "-".repeat(54) + "\n" + line("NET PAY", p.netPay()) + "\n"
                + "Calculation: (basic + allowance) × attendance ratio + overtime − deductions\n";
    }

    private String line(String label, BigDecimal amount) {
        return String.format(Locale.ROOT, "%-38s %14s%n", label, currency(amount));
    }
    private String currency(BigDecimal amount) { return "₹ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    private void savePayslip(String content, Employee e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("payslip-" + safeName(e.id()) + ".txt"));
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try { Files.writeString(chooser.getSelectedFile().toPath(), content, StandardCharsets.UTF_8); }
            catch (IOException ex) { error("Could not save payslip: " + ex.getMessage()); }
        }
    }

    private String safeName(String s) { return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("payroll-export.csv"));
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try { CsvStore.write(chooser.getSelectedFile().toPath(), employees.values());
                message("CSV exported successfully.", "Export complete"); }
            catch (IOException ex) { error("Could not export CSV: " + ex.getMessage()); }
        }
    }

    private void putEmployee(Employee e) {
        employees.put(e.id(), e); employeeIndex.put(e.id(), e);
        rebuildEmployeeList();
    }
    private void rebuildEmployeeList() {
        employeeList.clear(); employeeList.addAll(employees.values());
    }

    private void refreshTable() {
        if (tableModel == null) return;
        tableModel.setRowCount(0);
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (Employee e : employeeList) {
            String searchable = (e.id() + " " + e.name() + " " + e.department() + " " + e.status()).toLowerCase(Locale.ROOT);
            if (!searchable.contains(query)) continue;
            tableModel.addRow(new Object[]{e.id(), e.name(), e.department(), e.status(), currency(e.payroll().netPay())});
        }
        BigDecimal total = BigDecimal.ZERO; int active = 0;
        for (Employee e : employeeList) if (e.status().equals("Active")) { active++; total = total.add(e.payroll().netPay()); }
        summaryLabel.setText(String.format("%d employees  |  %d active  |  Active payroll total: %s  |  Records saved in %s",
                employeeList.size(), active, currency(total), DATA_FILE));
    }

    private void loadEmployees() {
        try {
            for (Employee e : CsvStore.read(DATA_FILE)) {
                if (e.payroll().netPay().signum() >= 0) putEmployee(e);
            }
        } catch (IOException | RuntimeException ex) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Could not load saved records. Starting with an empty directory.\n" + ex.getMessage(),
                    "Data file warning", JOptionPane.WARNING_MESSAGE));
        }
    }

    private void persist() {
        try { CsvStore.write(DATA_FILE, employees.values()); }
        catch (IOException ex) { error("Record changed in memory but could not be saved: " + ex.getMessage()); }
    }

    private void message(String text, String title) { JOptionPane.showMessageDialog(null, text, title, JOptionPane.INFORMATION_MESSAGE); }
    private void error(String text) { JOptionPane.showMessageDialog(null, text, "Check input", JOptionPane.ERROR_MESSAGE); }

    /** Immutable employee record. */
    static final class Employee {
        private final String id, name, department, email, status;
        private final BigDecimal basic, allowances, deductions, overtimeHours, overtimeRate;
        private final int paidDays, workingDays;

        Employee(String id, String name, String department, String email, String status,
                 BigDecimal basic, BigDecimal allowances, BigDecimal deductions,
                 BigDecimal overtimeHours, BigDecimal overtimeRate, int paidDays, int workingDays) {
            this.id = id.trim(); this.name = name.trim(); this.department = department.trim();
            this.email = email.trim(); this.status = status.equalsIgnoreCase("active") ? "Active" : "Inactive";
            this.basic = basic; this.allowances = allowances; this.deductions = deductions;
            this.overtimeHours = overtimeHours; this.overtimeRate = overtimeRate;
            this.paidDays = paidDays; this.workingDays = workingDays;
        }
        String id() { return id; } String name() { return name; } String department() { return department; }
        String email() { return email; } String status() { return status; } BigDecimal basic() { return basic; }
        BigDecimal allowances() { return allowances; } BigDecimal deductions() { return deductions; }
        BigDecimal overtimeHours() { return overtimeHours; } BigDecimal overtimeRate() { return overtimeRate; }
        int paidDays() { return paidDays; } int workingDays() { return workingDays; }
        Employee withStatus(String newStatus) {
            return new Employee(id, name, department, email, newStatus, basic, allowances, deductions,
                    overtimeHours, overtimeRate, paidDays, workingDays);
        }
        PayBreakdown payroll() {
            BigDecimal attendanceDays = BigDecimal.valueOf(paidDays);
            BigDecimal workDays = BigDecimal.valueOf(workingDays);
            BigDecimal proratedBasic = basic.multiply(attendanceDays).divide(workDays, 2, RoundingMode.HALF_UP);
            BigDecimal proratedAllowances = allowances.multiply(attendanceDays).divide(workDays, 2, RoundingMode.HALF_UP);
            BigDecimal overtime = overtimeHours.multiply(overtimeRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gross = proratedBasic.add(proratedAllowances).add(overtime).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(deductions).setScale(2, RoundingMode.HALF_UP);
            return new PayBreakdown(proratedBasic, proratedAllowances, overtime, gross, deductions, net);
        }
    }

    record PayBreakdown(BigDecimal proratedBasic, BigDecimal proratedAllowances,
                        BigDecimal overtimePay, BigDecimal grossPay, BigDecimal deductions, BigDecimal netPay) { }

    /** CSV persistence with quoted-field escaping; employee ID is the primary key. */
    static final class CsvStore {
        private static final String HEADER = "id,name,department,email,status,basic,allowances,deductions,overtimeHours,overtimeRate,paidDays,workingDays";
        static List<Employee> read(Path path) throws IOException {
            if (!Files.exists(path)) return List.of();
            List<Employee> result = new ArrayList<>();
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (i == 0 && lines.get(i).startsWith("id,")) continue;
                if (lines.get(i).isBlank()) continue;
                try {
                    List<String> f = parseLine(lines.get(i));
                    if (f.size() != 12) throw new IllegalArgumentException("Expected 12 fields");
                    result.add(new Employee(f.get(0), f.get(1), f.get(2), f.get(3), f.get(4),
                            new BigDecimal(f.get(5)), new BigDecimal(f.get(6)), new BigDecimal(f.get(7)),
                            new BigDecimal(f.get(8)), new BigDecimal(f.get(9)), Integer.parseInt(f.get(10)), Integer.parseInt(f.get(11))));
                } catch (RuntimeException ex) { throw new IOException("Invalid CSV data on line " + (i + 1) + ": " + ex.getMessage(), ex); }
            }
            return result;
        }
        static void write(Path path, Collection<Employee> employees) throws IOException {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> lines = new ArrayList<>(); lines.add(HEADER);
            for (Employee e : employees) {
                lines.add(String.join(",", quote(e.id()), quote(e.name()), quote(e.department()), quote(e.email()), quote(e.status()),
                        quote(e.basic().toPlainString()), quote(e.allowances().toPlainString()), quote(e.deductions().toPlainString()),
                        quote(e.overtimeHours().toPlainString()), quote(e.overtimeRate().toPlainString()),
                        quote(Integer.toString(e.paidDays())), quote(Integer.toString(e.workingDays()))));
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        }
        private static String quote(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
        private static List<String> parseLine(String line) {
            List<String> result = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else if (ch == '"') quoted = false;
                    else cell.append(ch);
                } else if (ch == ',' ) { result.add(cell.toString()); cell.setLength(0); }
                else if (ch == '"' && cell.length() == 0) quoted = true;
                else cell.append(ch);
            }
            if (quoted) throw new IllegalArgumentException("Unclosed quoted value");
            result.add(cell.toString()); return result;
        }
    }
}
