// ============================================================
//  Main.java  –  Application Entry Point
//  Initialises MySQL (blood_bank.sql / stored procedures) then
//  launches the Swing GUI on the EDT.
// ============================================================

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {

    public static void main(String[] args) {
        // #region agent log
        DebugLog.write("H4", "Main.java:main", "main started", "{}");
        // #endregion

        // ── 1. Initialise database + stored procedures ──
        DatabaseHelper.initDB();
        // #region agent log
        DebugLog.write("H4", "Main.java:main", "initDB returned", "{}");
        // #endregion

        // ── 2. Nimbus L&F – works well with custom button colours ──
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // default Swing L&F
            }
        }

        // ── 3. Launch GUI on the Event Dispatch Thread ──
        SwingUtilities.invokeLater(() -> {
            // #region agent log
            DebugLog.write("H4", "Main.java:invokeLater", "EDT runnable start", "{}");
            // #endregion
            try {
                BloodBankUI window = new BloodBankUI();
                window.setVisible(true);
                // #region agent log
                DebugLog.write("H4", "Main.java:invokeLater", "BloodBankUI visible", "{}");
                // #endregion
            } catch (Throwable t) {
                // #region agent log
                String err = t.getMessage() == null ? "" : t.getMessage().replace("\"", "'");
                DebugLog.write("H2", "Main.java:invokeLater", "BloodBankUI failed",
                        "{\"ex\":\"" + t.getClass().getSimpleName() + ": " + err + "\"}");
                // #endregion
                throw t;
            }
        });
    }
}
