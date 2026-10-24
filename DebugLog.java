// #region agent log
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/** Debug-mode NDJSON logger (session c493e0). */
final class DebugLog {
    private static final String LOG = "debug-c493e0.log";

    private DebugLog() {}

    static void write(String hypothesisId, String location, String message, String dataJson) {
        try (FileWriter fw = new FileWriter(LOG, StandardCharsets.UTF_8, true)) {
            String data = dataJson == null ? "{}" : dataJson;
            fw.write(String.format(
                    "{\"sessionId\":\"c493e0\",\"hypothesisId\":\"%s\",\"location\":\"%s\","
                            + "\"message\":\"%s\",\"data\":%s,\"timestamp\":%d}\n",
                    hypothesisId, esc(location), esc(message), data, System.currentTimeMillis()));
        } catch (Exception ignored) {
            // debug logging must not break the app
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
// #endregion
