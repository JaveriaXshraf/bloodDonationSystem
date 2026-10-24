// ============================================================
//  BloodBankUI.java  –  Main GUI Window (Swing)
//  4 Tabs:
//    1. Register Donor
//    2. View & Manage Donors
//    3. Request Blood  (NEW)
//    4. Manage Requests + Match Donors  (NEW)
//  OOP Concepts: Encapsulation, Composition, Polymorphism,
//                Overloading (getSummary, isEligible, getDetails)
// ============================================================

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class BloodBankUI extends JFrame {

    // ── Theme: clean medical palette, high-contrast controls ─────────────
    private static final Color PRIMARY    = new Color( 13, 115, 119);   // teal header
    private static final Color PRIMARY_DK = new Color(  8,  78,  82);
    private static final Color ACCENT     = new Color(198,  40,  40);   // blood red actions
    private static final Color BG         = new Color(240, 244, 248);
    private static final Color CARD       = Color.WHITE;
    private static final Color TEXT       = new Color( 26,  32,  44);
    private static final Color TEXT_MUTED = new Color( 74,  85, 104);
    private static final Color HIGHLIGHT  = new Color(204, 251, 241);
    private static final Color GREEN      = new Color( 22, 128,  57);
    private static final Color BLUE       = new Color( 37,  99, 235);
    private static final Color ORANGE     = new Color(194,  65,  12);
    private static final Color BTN_NEUTRAL  = new Color( 71,  85, 105);

    // #region agent log
    static {
        DebugLog.write("H2", "BloodBankUI.java:28", "static init past line 28 TEXT_MUTED",
                "{\"textMutedDefined\":true}");
    }
    // #endregion

    private static final String[] BLOOD_GROUPS = {"A+","A-","B+","B-","AB+","AB-","O+","O-"};

    // ── Tab 1 – Register Donor fields ─────────────────────────────────────
    private JTextField    tfName, tfAge, tfPhone, tfEmail, tfCity, tfAddress;
    private JComboBox<String> cbBloodGroup;

    // ── Tab 2 – View Donors ───────────────────────────────────────────────
    private JComboBox<String>  cbDonorFilter;
    private DefaultTableModel  donorTableModel;
    private JTable             donorTable;

    // ── Tab 3 – Request Blood fields ──────────────────────────────────────
    private JTextField    tfPatientName, tfPatientPhone, tfHospital;
    private JComboBox<String> cbNeededGroup, cbUrgency;

    // ── Tab 4 – Manage Requests ───────────────────────────────────────────
    private JComboBox<String>  cbRequestFilter;
    private DefaultTableModel  requestTableModel;
    private JTable             requestTable;
    // stores request IDs parallel to table rows (so we can update by ID)
    private List<BloodRequest> currentRequests;

    // ── Status bar ────────────────────────────────────────────────────────
    private JLabel lblStatus;

    // ─────────────────────────────────────────────────────────────────────
    //  CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────────
    public BloodBankUI() {
        // #region agent log
        DebugLog.write("H3", "BloodBankUI.java:constructor", "constructor start", "{}");
        // #endregion
        applyGlobalTheme();
        setTitle("Blood Bank Management System");
        setSize(1020, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(4, 4));

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildTabs(),      BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        refreshDonors(null);
        refreshRequests(null);
        // #region agent log
        DebugLog.write("H3", "BloodBankUI.java:constructor", "constructor done after refresh",
                "{\"donorRows\":" + donorTableModel.getRowCount()
                        + ",\"requestRows\":" + requestTableModel.getRowCount() + "}");
        // #endregion
    }

    // ─────────────────────────────────────────────────────────────────────
    //  HEADER
    // ─────────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PRIMARY);
        p.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JLabel title = new JLabel("Blood Bank Management System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Register donors  |  Request blood  |  Match & Fulfill");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(220, 245, 245));

        JPanel txt = new JPanel(new GridLayout(2, 1));
        txt.setOpaque(false);
        txt.add(title); txt.add(sub);
        p.add(txt, BorderLayout.WEST);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TABS
    // ─────────────────────────────────────────────────────────────────────
    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.setForeground(TEXT);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("  Register Donor  ",    buildRegisterTab());
        tabs.addTab("  View Donors      ",   buildDonorViewTab());
        tabs.addTab("  Request Blood    ",   buildRequestTab());
        tabs.addTab("  Manage Requests  ",   buildManageRequestsTab());
        return tabs;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TAB 1 – REGISTER DONOR
    // ─────────────────────────────────────────────────────────────────────
    private JPanel buildRegisterTab() {
        JPanel outer = padded(new BorderLayout(10, 10));
        JPanel form  = new JPanel(new GridBagLayout());
        form.setBackground(CARD);
        form.setBorder(titledBorder("New Donor Details"));

        GridBagConstraints c = gbc();
        cbBloodGroup = new JComboBox<>(BLOOD_GROUPS);
        tfName    = new JTextField(20); tfAge  = new JTextField(6);
        tfPhone   = new JTextField(14); tfEmail = new JTextField(20);
        tfCity    = new JTextField(16); tfAddress = new JTextField(24);

        addRow(form, c, 0, "Full Name *",   tfName);
        addRow(form, c, 1, "Age *",         tfAge);
        addRow(form, c, 2, "Phone *",       tfPhone);
        addRow(form, c, 3, "Email",         tfEmail);
        addRow(form, c, 4, "Blood Group *", cbBloodGroup);
        addRow(form, c, 5, "City",          tfCity);
        addRow(form, c, 6, "Address",       tfAddress);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        btns.setBackground(CARD);
        JButton btnSave  = makeBtn("Save Donor",  ACCENT, Color.WHITE);
        JButton btnClear = makeBtn("Clear Form",  BTN_NEUTRAL, Color.WHITE);
        btnSave .addActionListener(this::onSaveDonor);
        btnClear.addActionListener(e -> clearDonorForm());
        btns.add(btnSave); btns.add(btnClear);

        c.gridx = 0; c.gridy = 7; c.gridwidth = 2;
        form.add(btns, c);
        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TAB 2 – VIEW & MANAGE DONORS
    // ─────────────────────────────────────────────────────────────────────
    private JPanel buildDonorViewTab() {
        JPanel outer = padded(new BorderLayout(8, 8));

        // Filter bar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(BG);
        bar.add(fieldLabel("Filter by Blood Group:"));
        cbDonorFilter = new JComboBox<>(prepend("All", BLOOD_GROUPS));
        cbDonorFilter.addActionListener(e -> {
            String s = (String) cbDonorFilter.getSelectedItem();
            refreshDonors("All".equals(s) ? null : s);
        });
        bar.add(cbDonorFilter);
        JButton btnR = makeBtn("Refresh", BLUE, Color.WHITE);
        btnR.addActionListener(e -> refreshDonors(null));
        bar.add(btnR);
        outer.add(bar, BorderLayout.NORTH);

        // Table
        String[] cols = {"Name","Age","Phone","Blood Group","City","Donations","Eligible"};
        donorTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        donorTable = styledTable(donorTableModel);
        outer.add(new JScrollPane(donorTable), BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        btns.setBackground(BG);
        JButton btnDel    = makeBtn("Delete Donor",    ACCENT, Color.WHITE);
        JButton btnDonate = makeBtn("Record Donation", GREEN,   Color.WHITE);
        JButton btnStats  = makeBtn("Show Stats",      BLUE,    Color.WHITE);
        btnDel   .addActionListener(this::onDeleteDonor);
        btnDonate.addActionListener(this::onRecordDonation);
        btnStats .addActionListener(this::onShowStats);
        btns.add(btnDel); btns.add(btnDonate); btns.add(btnStats);
        outer.add(btns, BorderLayout.SOUTH);

        return outer;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TAB 3 – REQUEST BLOOD  (NEW)
    // ─────────────────────────────────────────────────────────────────────
    private JPanel buildRequestTab() {
        JPanel outer = padded(new BorderLayout(10, 10));

        // Info banner
        JLabel info = new JLabel(
            "<html><b>Need blood?</b> Fill in the patient details below. " +
            "We will find matching donors and update you.</html>");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        info.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200,230,255), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        info.setOpaque(true);
        info.setBackground(new Color(224, 242, 254));
        info.setForeground(TEXT);
        outer.add(info, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD);
        form.setBorder(titledBorder("Blood Request Form"));
        GridBagConstraints c = gbc();

        tfPatientName  = new JTextField(20);
        tfPatientPhone = new JTextField(14);
        tfHospital     = new JTextField(24);
        cbNeededGroup  = new JComboBox<>(BLOOD_GROUPS);
        cbUrgency      = new JComboBox<>(new String[]{"Normal","Urgent"});

        addRow(form, c, 0, "Patient Name *",      tfPatientName);
        addRow(form, c, 1, "Contact Phone *",      tfPatientPhone);
        addRow(form, c, 2, "Hospital / Location *",tfHospital);
        addRow(form, c, 3, "Blood Group Needed *", cbNeededGroup);
        addRow(form, c, 4, "Urgency",              cbUrgency);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        btns.setBackground(CARD);
        JButton btnSubmit = makeBtn("Submit Request", ACCENT, Color.WHITE);
        JButton btnClearR = makeBtn("Clear",          BTN_NEUTRAL, Color.WHITE);
        btnSubmit.addActionListener(this::onSubmitRequest);
        btnClearR.addActionListener(e -> clearRequestForm());
        btns.add(btnSubmit); btns.add(btnClearR);

        c.gridx = 0; c.gridy = 5; c.gridwidth = 2;
        form.add(btns, c);
        outer.add(form, BorderLayout.CENTER);

        // Quick match panel
        JPanel qm = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        qm.setBackground(BG);
        qm.setBorder(titledBorder("Quick Donor Search (before submitting)"));
        JComboBox<String> cbQuick = new JComboBox<>(BLOOD_GROUPS);
        JButton btnFind = makeBtn("Find Donors Now", ORANGE, Color.WHITE);
        btnFind.addActionListener(e -> {
            String bg = (String) cbQuick.getSelectedItem();
            List<Donor> found = DatabaseHelper.findMatchingDonors(bg);
            if (found.isEmpty()) {
                info("No donors registered for blood group " + bg + " yet.");
            } else {
                StringBuilder sb = new StringBuilder(
                    "Donors available for " + bg + ":\n\n");
                // POLYMORPHISM – Donor used as Person to call getSummary(true)
                for (Donor d : found) {
                    sb.append(((Person) d).getSummary(true)).append("\n");
                }
                JOptionPane.showMessageDialog(this, sb.toString(),
                    "Matching Donors – " + bg, JOptionPane.INFORMATION_MESSAGE);
            }
        });
        qm.add(fieldLabel("Blood Group:")); qm.add(cbQuick); qm.add(btnFind);
        outer.add(qm, BorderLayout.SOUTH);

        return outer;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TAB 4 – MANAGE REQUESTS  (NEW)
    // ─────────────────────────────────────────────────────────────────────
    private JPanel buildManageRequestsTab() {
        JPanel outer = padded(new BorderLayout(8, 8));

        // Filter bar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(BG);
        bar.add(fieldLabel("Filter by Status:"));
        cbRequestFilter = new JComboBox<>(new String[]{"All","Pending","Matched","Fulfilled"});
        cbRequestFilter.addActionListener(e -> {
            String s = (String) cbRequestFilter.getSelectedItem();
            refreshRequests("All".equals(s) ? null : s);
        });
        bar.add(cbRequestFilter);
        JButton btnR = makeBtn("Refresh", BLUE, Color.WHITE);
        btnR.addActionListener(e -> refreshRequests(null));
        bar.add(btnR);
        outer.add(bar, BorderLayout.NORTH);

        // Table
        String[] cols = {"ID","Patient","Blood Needed","Hospital","Phone","Urgency","Status"};
        requestTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        requestTable = styledTable(requestTableModel);
        outer.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        // Action buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        btns.setBackground(BG);

        JButton btnMatch   = makeBtn("Find Matching Donors",        ORANGE, Color.WHITE);
        JButton btnMark    = makeBtn("Mark as Matched",             GREEN,  Color.WHITE);
        JButton btnFulfill = makeBtn("Mark as Fulfilled",           BLUE,   Color.WHITE);
        JButton btnTxn     = makeBtn("Fulfill + Record Donation",   PRIMARY,Color.WHITE);
        JButton btnDelReq  = makeBtn("Delete Request",              ACCENT, Color.WHITE);

        btnMatch  .addActionListener(this::onFindMatchForRequest);
        btnMark   .addActionListener(e -> onChangeStatus("Matched"));
        btnFulfill.addActionListener(e -> onChangeStatus("Fulfilled"));
        btnTxn    .addActionListener(this::onFulfillWithDonation);
        btnDelReq .addActionListener(this::onDeleteRequest);

        btns.add(btnMatch); btns.add(btnMark); btns.add(btnFulfill);
        btns.add(btnTxn); btns.add(btnDelReq);
        outer.add(btns, BorderLayout.SOUTH);

        return outer;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  STATUS BAR
    // ─────────────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        p.setBackground(PRIMARY_DK);
        lblStatus = new JLabel("Ready — connected to MySQL database blood_bank (stored procedures).");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(230, 250, 250));
        p.add(lblStatus);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  EVENT HANDLERS – DONORS
    // ═════════════════════════════════════════════════════════════════════

    private void onSaveDonor(ActionEvent e) {
        if (tfName.getText().isBlank() || tfAge.getText().isBlank() || tfPhone.getText().isBlank()) {
            warn("Name, Age and Phone are required.", "Validation"); return;
        }
        int age;
        try { age = Integer.parseInt(tfAge.getText().trim()); }
        catch (NumberFormatException ex) { warn("Age must be a number.","Invalid Age"); return; }

        Donor d = new Donor(tfName.getText().trim(), age,
                tfPhone.getText().trim(), tfEmail.getText().trim(),
                (String) cbBloodGroup.getSelectedItem(),
                tfCity.getText().trim(), tfAddress.getText().trim());

        if (!d.isEligible()) { warn("Donor must be 18–65 years old.", "Not Eligible"); return; }

        if (DatabaseHelper.addDonor(d)) {
            showDonorRegistrationSuccess(d);
            status("Donor '" + d.getName() + "' saved.");
            clearDonorForm();
            refreshDonors(null);
        } else { status("ERROR: Could not save donor."); }
    }

    private void onDeleteDonor(ActionEvent e) {
        int row = donorTable.getSelectedRow();
        if (row < 0) { info("Select a donor first."); return; }
        String name = (String) donorTableModel.getValueAt(row, 0);
        String phone= (String) donorTableModel.getValueAt(row, 2);
        if (JOptionPane.showConfirmDialog(this, "Delete donor: " + name + "?",
                "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            status(DatabaseHelper.deleteDonor(name, phone) ? "Donor deleted." : "Delete failed.");
            refreshDonors(null);
        }
    }

    private void onRecordDonation(ActionEvent e) {
        int row = donorTable.getSelectedRow();
        if (row < 0) { info("Select a donor first."); return; }
        String name = (String) donorTableModel.getValueAt(row, 0);
        String phone= (String) donorTableModel.getValueAt(row, 2);
        status(DatabaseHelper.incrementDonationCount(name, phone)
                ? "Donation recorded for " + name + "." : "Update failed.");
        refreshDonors(null);
    }

    private void onShowStats(ActionEvent e) {
        List<Donor> all = DatabaseHelper.getAllDonors(null);
        if (all.isEmpty()) { info("No donors yet."); return; }
        long donations = all.stream().mapToLong(Donor::getDonationCount).sum();
        long eligible  = all.stream().filter(Donor::isEligible).count();
        String top = all.stream()
                .max((a, b) -> a.getDonationCount() - b.getDonationCount())
                .map(d -> ((Person) d).getSummary(true))   // POLYMORPHISM + OVERLOADING
                .orElse("N/A");
        List<BloodRequest> reqs = DatabaseHelper.getAllRequests(null);
        long pending   = reqs.stream().filter(r -> "Pending".equals(r.getStatus())).count();
        long fulfilled = reqs.stream().filter(r -> "Fulfilled".equals(r.getStatus())).count();

        JOptionPane.showMessageDialog(this,
            "Blood Bank Statistics\n" +
            "─────────────────────────────\n" +
            "Total Donors    : " + all.size()   + "\n" +
            "Eligible Donors : " + eligible     + "\n" +
            "Total Donations : " + donations    + "\n" +
            "Top Donor       : " + top          + "\n" +
            "─────────────────────────────\n" +
            "Total Requests  : " + reqs.size()  + "\n" +
            "Pending         : " + pending      + "\n" +
            "Fulfilled       : " + fulfilled,
            "Statistics", JOptionPane.INFORMATION_MESSAGE);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  EVENT HANDLERS – BLOOD REQUESTS
    // ═════════════════════════════════════════════════════════════════════

    private void onSubmitRequest(ActionEvent e) {
        if (tfPatientName.getText().isBlank() || tfPatientPhone.getText().isBlank()
                || tfHospital.getText().isBlank()) {
            warn("Patient Name, Phone and Hospital are required.", "Validation"); return;
        }
        BloodRequest req = new BloodRequest(
                tfPatientName.getText().trim(),
                (String) cbNeededGroup.getSelectedItem(),
                tfHospital.getText().trim(),
                tfPatientPhone.getText().trim(),
                (String) cbUrgency.getSelectedItem());

        if (DatabaseHelper.addRequest(req)) {
            int newId = DatabaseHelper.getLatestRequestId();
            if (newId > 0) {
                req.setId(newId);
            }
            showBloodRequestSuccess(req);
            status("Blood request submitted for " + req.getPatientName() + ".");
            clearRequestForm();
            refreshRequests(null);
        } else {
            status("ERROR: Could not submit request.");
        }
    }

    /** Find donors that match the selected request's blood group */
    private void onFindMatchForRequest(ActionEvent e) {
        int row = requestTable.getSelectedRow();
        if (row < 0) { info("Select a request from the table first."); return; }

        String bloodNeeded = (String) requestTableModel.getValueAt(row, 2);
        String patient     = (String) requestTableModel.getValueAt(row, 1);

        List<Donor> donors = DatabaseHelper.findMatchingDonors(bloodNeeded);
        if (donors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No donors with blood group " + bloodNeeded + " are registered.\n" +
                "Consider registering more donors or contacting nearby hospitals.",
                "No Match Found", JOptionPane.WARNING_MESSAGE);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Matching donors for patient '").append(patient)
              .append("' (needs ").append(bloodNeeded).append("):\n\n");
            for (Donor d : donors) {
                // POLYMORPHISM – Person reference, calls overloaded getSummary(true)
                sb.append(((Person) d).getSummary(true))
                  .append(" | Donations: ").append(d.getDonationCount())
                  .append("\n");
            }
            sb.append("\nContact the donor(s) above to arrange donation.");
            JOptionPane.showMessageDialog(this, sb.toString(),
                "Matching Donors Found", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onChangeStatus(String newStatus) {
        int row = requestTable.getSelectedRow();
        if (row < 0) { info("Select a request first."); return; }
        int id = currentRequests.get(row).getId();
        boolean ok = DatabaseHelper.updateRequestStatus(id, newStatus);
        status(ok ? "Request marked as " + newStatus + "." : "Update failed.");
        refreshRequests(null);
    }

    /** Uses MySQL transaction: fulfill request + increment donor donation count. */
    private void onFulfillWithDonation(ActionEvent e) {
        int row = requestTable.getSelectedRow();
        if (row < 0) { info("Select a request from the table first."); return; }

        int id = currentRequests.get(row).getId();
        String bloodNeeded = (String) requestTableModel.getValueAt(row, 2);
        List<Donor> donors = DatabaseHelper.findMatchingDonors(bloodNeeded);
        if (donors.isEmpty()) {
            warn("No donors with blood group " + bloodNeeded + " registered.", "No Donors");
            return;
        }

        String[] choices = donors.stream()
                .map(d -> d.getName() + " (" + d.getPhone() + ") — " + d.getBloodGroup())
                .toArray(String[]::new);
        String picked = (String) JOptionPane.showInputDialog(this,
                "Select the donor who donated (transaction will update request + donation count):",
                "Fulfill with Donation",
                JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (picked == null) return;

        int idx = -1;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equals(picked)) { idx = i; break; }
        }
        if (idx < 0) return;

        Donor donor = donors.get(idx);
        boolean ok = DatabaseHelper.fulfillRequestWithDonation(id, donor.getName(), donor.getPhone());
        status(ok
                ? "Request fulfilled and donation recorded for " + donor.getName() + " (transaction committed)."
                : "Transaction failed — no changes saved.");
        refreshRequests(null);
        refreshDonors(null);
    }

    private void onDeleteRequest(ActionEvent e) {
        int row = requestTable.getSelectedRow();
        if (row < 0) { info("Select a request first."); return; }
        int    id      = currentRequests.get(row).getId();
        String patient = (String) requestTableModel.getValueAt(row, 1);
        if (JOptionPane.showConfirmDialog(this, "Delete request for: " + patient + "?",
                "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            status(DatabaseHelper.deleteRequest(id) ? "Request deleted." : "Delete failed.");
            refreshRequests(null);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DATA REFRESH HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private void refreshDonors(String filter) {
        donorTableModel.setRowCount(0);
        for (Donor d : DatabaseHelper.getAllDonors(filter)) {
            donorTableModel.addRow(new Object[]{
                d.getName(), d.getAge(), d.getPhone(),
                d.getBloodGroup(), d.getContactInfo().getCity(),
                d.getDonationCount(), d.isEligible() ? "Yes" : "No"
            });
        }
        status("Donors loaded: " + donorTableModel.getRowCount());
    }

    private void refreshRequests(String statusFilter) {
        requestTableModel.setRowCount(0);
        currentRequests = DatabaseHelper.getAllRequests(statusFilter);
        for (BloodRequest r : currentRequests) {
            requestTableModel.addRow(new Object[]{
                r.getId(), r.getPatientName(), r.getBloodGroupNeeded(),
                r.getHospital(), r.getContactPhone(), r.getUrgency(), r.getStatus()
            });
        }
        status("Requests loaded: " + requestTableModel.getRowCount());
    }

    // ═════════════════════════════════════════════════════════════════════
    //  UI UTILITY METHODS
    // ═════════════════════════════════════════════════════════════════════

    private void clearDonorForm() {
        tfName.setText(""); tfAge.setText(""); tfPhone.setText("");
        tfEmail.setText(""); tfCity.setText(""); tfAddress.setText("");
        cbBloodGroup.setSelectedIndex(0);
    }
    private void clearRequestForm() {
        tfPatientName.setText(""); tfPatientPhone.setText(""); tfHospital.setText("");
        cbNeededGroup.setSelectedIndex(0); cbUrgency.setSelectedIndex(0);
    }

    private void addRow(JPanel p, GridBagConstraints c, int row,
                        String label, JComponent field) {
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        p.add(fieldLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        p.add(field, c);
    }

    private void applyGlobalTheme() {
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("TextField.background", CARD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("ComboBox.background", CARD);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", HIGHLIGHT);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
    }

    private JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(TEXT);
        return lbl;
    }

    /** Buttons always show readable text (no hover needed) on Windows L&F. */
    private JButton makeBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setFocusPainted(false);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setMargin(new Insets(10, 18, 10, 18));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 2),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(26);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.getTableHeader().setBackground(PRIMARY_DK);
        t.getTableHeader().setForeground(Color.WHITE);
        t.setGridColor(new Color(226, 232, 240));
        t.setSelectionBackground(HIGHLIGHT);
        t.setSelectionForeground(TEXT);
        t.setBackground(CARD);
        t.setForeground(TEXT);
        return t;
    }

    private JPanel padded(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        return p;
    }

    private GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 10, 7, 10);
        c.fill   = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(PRIMARY_DK, 2), title,
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 13), PRIMARY_DK);
    }

    private String[] prepend(String first, String[] rest) {
        String[] arr = new String[rest.length + 1];
        arr[0] = first;
        System.arraycopy(rest, 0, arr, 1, rest.length);
        return arr;
    }

    private void status(String msg) { lblStatus.setText(msg); }

    /** Popup after donor registration + optional PDF receipt. */
    private void showDonorRegistrationSuccess(Donor d) {
        String message =
            "Registration successful!\n\n"
            + "Thank you, " + d.getName() + ".\n"
            + "You are now registered as a blood donor.\n\n"
            + "Blood group : " + d.getBloodGroup() + "\n"
            + "Phone       : " + d.getPhone() + "\n"
            + "City        : " + d.getContactInfo().getCity() + "\n\n"
            + "Click \"Save PDF\" to keep a printable copy in the receipts folder.";

        Object[] buttons = {"Save PDF", "Close"};
        int choice = JOptionPane.showOptionDialog(this, message, "Donor Registered",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, buttons, buttons[1]);

        if (choice == 0) {
            offerPdfReceipt(PdfHelper.saveDonorReceipt(d), "Donor registration");
        }
    }

    /** Popup after blood request + optional PDF receipt. */
    private void showBloodRequestSuccess(BloodRequest req) {
        String message =
            "Blood request submitted successfully!\n\n"
            + req.getDetails(true) + "\n\n"
            + "Click \"Save PDF\" to keep a printable copy for your records.";

        Object[] buttons = {"Save PDF", "Close"};
        int choice = JOptionPane.showOptionDialog(this, message, "Request Submitted",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, buttons, buttons[1]);

        if (choice == 0) {
            offerPdfReceipt(PdfHelper.saveBloodRequestReceipt(req), "Blood request");
        }
    }

    private void offerPdfReceipt(String savedPath, String label) {
        if (savedPath == null) {
            warn("Could not create PDF file. Check that the app can write to the receipts folder.",
                    "PDF Error");
            return;
        }
        Object[] buttons = {"Open PDF", "Close"};
        int choice = JOptionPane.showOptionDialog(this,
                label + " receipt saved as PDF:\n\n" + savedPath
                + "\n\n(Folder: receipts\\ in your project directory)",
                "PDF Saved",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, buttons, buttons[1]);
        if (choice == 0) {
            PdfHelper.openPdfIfPossible(savedPath);
        }
        status("PDF receipt saved: " + savedPath);
    }

    private void warn(String msg, String title) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.WARNING_MESSAGE);
    }
    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }
}
