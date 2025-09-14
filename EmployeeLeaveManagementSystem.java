import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.*;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmployeeLeaveManagementSystem {

    // ------------------------- Data classes -------------------------
    static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;
        int empId;
        String name;
        int leaveBalance; // in days

        Employee(int id, String name, int leaveBalance) {
            this.empId = id;
            this.name = name;
            this.leaveBalance = leaveBalance;
        }

        @Override
        public String toString() {
            return empId + " - " + name + " (Balance: " + leaveBalance + ")";
        }
    }

    static class LeaveApplication implements Serializable {
        private static final long serialVersionUID = 1L;
        int leaveId;
        int empId;
        String startDate; // yyyy-MM-dd
        String endDate;   // yyyy-MM-dd
        String reason;
        String status; // Pending / Approved / Rejected
        int daysRequested;

        LeaveApplication(int leaveId, int empId, String startDate, String endDate, String reason, int daysRequested) {
            this.leaveId = leaveId;
            this.empId = empId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.reason = reason;
            this.status = "Pending";
            this.daysRequested = daysRequested;
        }
    }

    // ----------------------- Persistence & Manager -----------------------
    static class LeaveManager {
        Map<Integer, Employee> employees = new HashMap<>();
        Map<Integer, LeaveApplication> applications = new HashMap<>();
        int nextLeaveId = 1;

        private final File empFile = new File("employees.ser");
        private final File appFile = new File("applications.ser");

        LeaveManager() {
            load();
            if (employees.isEmpty()) {
                // create some dummy employees if none exist
                addEmployee(new Employee(101, "Rahul Sharma", 20));
                addEmployee(new Employee(102, "Priya Singh", 18));
                addEmployee(new Employee(103, "Amit Verma", 15));
                save();
            }
        }

        void addEmployee(Employee e) {
            employees.put(e.empId, e);
        }

        synchronized LeaveApplication applyLeave(int empId, String start, String end, String reason) throws IllegalArgumentException {
            if (!employees.containsKey(empId)) throw new IllegalArgumentException("Employee not found");
            int days = calcDays(start, end);
            if (days <= 0) throw new IllegalArgumentException("Invalid date range");
            Employee e = employees.get(empId);
            if (e.leaveBalance < days) throw new IllegalArgumentException("Not enough leave balance");

            LeaveApplication la = new LeaveApplication(nextLeaveId++, empId, start, end, reason, days);
            applications.put(la.leaveId, la);
            save();
            return la;
        }

        synchronized void approve(int leaveId) throws IllegalArgumentException {
            LeaveApplication la = applications.get(leaveId);
            if (la == null) throw new IllegalArgumentException("Leave not found");
            if (!la.status.equals("Pending")) throw new IllegalArgumentException("Leave already processed");

            Employee e = employees.get(la.empId);
            if (e.leaveBalance < la.daysRequested) throw new IllegalArgumentException("Employee has insufficient balance at approval time");
            e.leaveBalance -= la.daysRequested;
            la.status = "Approved";
            save();
        }

        synchronized void reject(int leaveId) throws IllegalArgumentException {
            LeaveApplication la = applications.get(leaveId);
            if (la == null) throw new IllegalArgumentException("Leave not found");
            if (!la.status.equals("Pending")) throw new IllegalArgumentException("Leave already processed");
            la.status = "Rejected";
            save();
        }

        List<LeaveApplication> listApplications() {
            return new ArrayList<>(applications.values());
        }

        List<LeaveApplication> listApplicationsForEmployee(int empId) {
            List<LeaveApplication> out = new ArrayList<>();
            for (LeaveApplication la : applications.values()) if (la.empId == empId) out.add(la);
            return out;
        }

        Employee getEmployee(int empId) {
            return employees.get(empId);
        }

        Collection<Employee> getAllEmployees() { return employees.values(); }

        private int calcDays(String start, String end) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                sdf.setLenient(false);
                Date s = sdf.parse(start);
                Date e = sdf.parse(end);
                long diff = e.getTime() - s.getTime();
                int days = (int) (diff / (1000L * 60 * 60 * 24)) + 1;
                return days;
            } catch (ParseException ex) {
                return -1;
            }
        }

        private void load() {
            // load employees
            if (empFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(empFile))) {
                    Object obj = ois.readObject();
                    if (obj instanceof Map) {
                        //noinspection unchecked
                        employees = (Map<Integer, Employee>) obj;
                    }
                } catch (Exception ignored) {}
            }
            // load applications
            if (appFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(appFile))) {
                    Object obj = ois.readObject();
                    if (obj instanceof Map) {
                        //noinspection unchecked
                        applications = (Map<Integer, LeaveApplication>) obj;
                        // set nextLeaveId to avoid collisions
                        for (int id : applications.keySet()) nextLeaveId = Math.max(nextLeaveId, id + 1);
                    }
                } catch (Exception ignored) {}
            }
        }

        private void save() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(empFile))) {
                oos.writeObject(employees);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(appFile))) {
                oos.writeObject(applications);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // -------------------------- Simple GUI --------------------------
    static class SimpleGUI {
        private final LeaveManager manager;

        JFrame frame;
        JComboBox<String> cmbEmployees;
        DefaultTableModel historyModel;
        DefaultTableModel adminModel;

        SimpleGUI(LeaveManager manager) {
            this.manager = manager;
            SwingUtilities.invokeLater(this::build);
        }

        private void build() {
            frame = new JFrame("Employee Leave Management - Simple");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 600);
            frame.setLocationRelativeTo(null);

            JTabbedPane tabs = new JTabbedPane();
            tabs.add("Employee", buildEmployeePanel());
            tabs.add("Admin", buildAdminPanel());

            frame.add(tabs);
            frame.setVisible(true);
        }

        private JPanel buildEmployeePanel() {
            JPanel p = new JPanel(new BorderLayout(8,8));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

            cmbEmployees = new JComboBox<>();
            for (Employee e : manager.getAllEmployees()) cmbEmployees.addItem(e.toString());
            top.add(new JLabel("Select Employee:"));
            top.add(cmbEmployees);

            JButton btnRefresh = new JButton("Refresh");
            btnRefresh.addActionListener(ev -> refreshEmployeeView());
            top.add(btnRefresh);

            p.add(top, BorderLayout.NORTH);

            // center split: apply form + history
            JPanel center = new JPanel(new GridLayout(1,2,8,8));
            center.add(buildApplyPanel());
            center.add(buildHistoryPanel());
            p.add(center, BorderLayout.CENTER);
            return p;
        }

        private JPanel buildApplyPanel() {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(BorderFactory.createTitledBorder("Apply for Leave"));

            p.add(new JLabel("Start Date (yyyy-MM-dd):"));
            JTextField txtStart = new JTextField();
            p.add(txtStart);

            p.add(new JLabel("End Date (yyyy-MM-dd):"));
            JTextField txtEnd = new JTextField();
            p.add(txtEnd);

            p.add(new JLabel("Reason:"));
            JTextArea txtReason = new JTextArea(4, 20);
            p.add(new JScrollPane(txtReason));

            JButton btnApply = new JButton("Apply");
            btnApply.addActionListener(ev -> {
                String sel = (String) cmbEmployees.getSelectedItem();
                if (sel == null) return;
                int empId = Integer.parseInt(sel.split(" ")[0]);
                try {
                    LeaveApplication la = manager.applyLeave(empId, txtStart.getText().trim(), txtEnd.getText().trim(), txtReason.getText().trim());
                    JOptionPane.showMessageDialog(frame, "Applied successfully. Leave ID: " + la.leaveId);
                    refreshEmployeeView();
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            p.add(btnApply);
            return p;
        }

        private JPanel buildHistoryPanel() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createTitledBorder("Leave History"));

            historyModel = new DefaultTableModel(new Object[]{"LeaveId","Start","End","Days","Reason","Status"},0) {
                public boolean isCellEditable(int row, int col) { return false; }
            };
            JTable tbl = new JTable(historyModel);
            p.add(new JScrollPane(tbl), BorderLayout.CENTER);
            return p;
        }

        private JPanel buildAdminPanel() {
            JPanel p = new JPanel(new BorderLayout(8,8));
            p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

            adminModel = new DefaultTableModel(new Object[]{"LeaveId","EmpId","EmpName","Start","End","Days","Reason","Status"},0) {
                public boolean isCellEditable(int r,int c){return false;}
            };
            JTable tbl = new JTable(adminModel);
            p.add(new JScrollPane(tbl), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnApprove = new JButton("Approve");
            JButton btnReject = new JButton("Reject");
            JButton btnRefresh = new JButton("Refresh");

            btnApprove.addActionListener(ev -> {
                int row = tbl.getSelectedRow();
                if (row == -1) { JOptionPane.showMessageDialog(frame, "Select a leave first."); return; }
                int leaveId = (int) adminModel.getValueAt(row,0);
                try {
                    manager.approve(leaveId);
                    JOptionPane.showMessageDialog(frame, "Approved.");
                    refreshAdminView();
                    refreshEmployeeView();
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            btnReject.addActionListener(ev -> {
                int row = tbl.getSelectedRow();
                if (row == -1) { JOptionPane.showMessageDialog(frame, "Select a leave first."); return; }
                int leaveId = (int) adminModel.getValueAt(row,0);
                try {
                    manager.reject(leaveId);
                    JOptionPane.showMessageDialog(frame, "Rejected.");
                    refreshAdminView();
                    refreshEmployeeView();
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            btnRefresh.addActionListener(ev -> refreshAdminView());

            bottom.add(btnApprove);
            bottom.add(btnReject);
            bottom.add(btnRefresh);
            p.add(bottom, BorderLayout.SOUTH);
            refreshAdminView();
            return p;
        }

        private void refreshEmployeeView() {
            historyModel.setRowCount(0);
            String sel = (String) cmbEmployees.getSelectedItem();
            if (sel == null) return;
            int empId = Integer.parseInt(sel.split(" ")[0]);
            Employee e = manager.getEmployee(empId);
            frame.setTitle("Employee Leave Management - " + e.name + " (Balance: " + e.leaveBalance + ")");
            for (LeaveApplication la : manager.listApplicationsForEmployee(empId)) {
                historyModel.addRow(new Object[]{la.leaveId, la.startDate, la.endDate, la.daysRequested, la.reason, la.status});
            }
        }

        private void refreshAdminView() {
            adminModel.setRowCount(0);
            for (LeaveApplication la : manager.listApplications()) {
                Employee e = manager.getEmployee(la.empId);
                String nm = e == null ? "Unknown" : e.name;
                adminModel.addRow(new Object[]{la.leaveId, la.empId, nm, la.startDate, la.endDate, la.daysRequested, la.reason, la.status});
            }
        }
    }

    // ---------------------------- Main ----------------------------
    public static void main(String[] args) {
        try {
            LeaveManager mgr = new LeaveManager();
            new SimpleGUI(mgr);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Something went wrong while starting the app: " + ex.getMessage());
        }
    }
}
