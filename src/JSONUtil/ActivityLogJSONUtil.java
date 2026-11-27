package JSONUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import storage.AppDataPaths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

public class ActivityLogJSONUtil {
    private static final File FILE = AppDataPaths.dataFile("logs.json");
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = JacksonTreeIO.mapper();

    public static synchronized void log(String user, String action, String details) {
        ArrayList<LogEntry> entries = readLogEntries();
        LogEntry entry = new LogEntry();
        entry.timestamp = dtf.format(LocalDateTime.now());
        entry.user = user;
        entry.action = action;
        entry.details = details;
        entries.add(entry);
        writeLogEntries(entries);
    }

    public static synchronized ArrayList<String> getLogEntries() {
        ArrayList<String> output = new ArrayList<>();
        for (LogEntry log : readLogEntries()) {
            String line = String.format("[%s] User: %s | Action: %s | Details: %s",
                    log.timestamp != null ? log.timestamp : "",
                    log.user != null ? log.user : "",
                    log.action != null ? log.action : "",
                    log.details != null ? log.details : "");
            output.add(line);
        }
        return output;
    }

    public static synchronized ArrayList<String> rejectedSubmissions(String ranger) {
        ArrayList<String> result = new ArrayList<>();
        for (LogEntry entry : readLogEntries()) {
            if (ranger.equals(entry.user) && "SUBMISSION_REJECTED".equals(entry.action)) {
                result.add("Rejected  ·  " + entry.details + "  ·  " + entry.timestamp);
            }
        }
        return result;
    }

    /**
     * Activity-log lines that concern one tree, newest first, as {@code "yyyy-MM-dd HH:mm | user | event"}.
     * Older entries that predate tree IDs in the log are matched by their species and location.
     */
    public static synchronized ArrayList<String> historyForTree(int treeId, String species, String location) {
        java.util.regex.Pattern idRef = java.util.regex.Pattern.compile("(?:ID:?\\s*|#)" + treeId + "(?!\\d)");
        String legacyAdd = species == null || location == null ? null : "Species: " + species + ", Location: " + location;
        ArrayList<String> out = new ArrayList<>();
        for (LogEntry e : readLogEntries()) {
            String action = e.action == null ? "" : e.action;
            String details = e.details == null ? "" : e.details;
            boolean mentionsId = idRef.matcher(details).find();
            String event = null;
            switch (action) {
                case "ADD_TREE" -> {
                    if (mentionsId || (legacyAdd != null && details.startsWith(legacyAdd))) {
                        String status = afterKey(details, "Status: ");
                        event = "Registered in the inventory" + (status != null ? " (" + status + ")" : "");
                    }
                }
                case "SUBMIT_PENDING_TREE" -> {
                    if (mentionsId || (legacyAdd != null && details.startsWith(legacyAdd))) event = "Submitted by ranger for review";
                }
                case "APPROVE_PENDING_TREE" -> {
                    if (mentionsId) event = "Approved by admin and added to the inventory";
                }
                case "UPDATE_TREE" -> {
                    if (mentionsId) {
                        int dot = details.indexOf(" · ");
                        event = dot >= 0 ? details.substring(dot + 3) : "Record updated";
                    }
                }
                case "DELETE_TREE" -> {
                    if (mentionsId) event = "Removed from the inventory (archived)";
                }
                case "RESTORE_TREE" -> {
                    if (mentionsId) event = "Restored from the archive";
                }
                default -> { }
            }
            if (event != null) {
                String when = e.timestamp == null ? "" : (e.timestamp.length() >= 16 ? e.timestamp.substring(0, 16) : e.timestamp);
                out.add(when + " | " + (e.user == null ? "" : e.user) + " | " + event);
            }
        }
        Collections.reverse(out);
        return out;
    }

    private static String afterKey(String details, String key) {
        int i = details.indexOf(key);
        if (i < 0) return null;
        String rest = details.substring(i + key.length());
        int comma = rest.indexOf(',');
        return (comma >= 0 ? rest.substring(0, comma) : rest).trim();
    }

    /**
     * Human-readable summary of what changed in an edit, e.g. {@code " · Status: Healthy → Monitoring · Height: 3.4 → 3.6 m"}.
     * Empty when nothing visible changed.
     */
    public static String describeChanges(Model.Tree before, Model.Tree after) {
        if (before == null || after == null) return "";
        StringBuilder sb = new StringBuilder();
        change(sb, "Status", before.status, after.status, "");
        change(sb, "Height", num(before.height), num(after.height), " m");
        change(sb, "Diameter", num(before.diameter), num(after.diameter), " cm");
        change(sb, "Species", before.species, after.species, "");
        change(sb, "Location", before.location, after.location, "");
        change(sb, "Planted", before.plantedDate, after.plantedDate, "");
        if (!java.util.Objects.equals(blankToNull(before.notes), blankToNull(after.notes))) sb.append(" · Field notes updated");
        if (after.imageBytes != null && after.imageBytes.length > 0) {
            byte[] old = before.imageBytes != null && before.imageBytes.length > 0
                    ? before.imageBytes : storage.ImageStorage.loadTreeImageById(before.id);
            if (old == null || old.length == 0) sb.append(" · Photo added");
            else if (!java.util.Arrays.equals(old, after.imageBytes)) sb.append(" · Photo updated");
        }
        return sb.toString();
    }

    private static void change(StringBuilder sb, String label, String a, String b, String unit) {
        if (java.util.Objects.equals(blankToNull(a), blankToNull(b))) return;
        sb.append(" · ").append(label).append(": ").append(a == null || a.isBlank() ? "—" : a.trim())
                .append(" → ").append(b == null || b.isBlank() ? "—" : b.trim()).append(unit);
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ArrayList<LogEntry> readLogEntries() {
        if (!FILE.exists()) {
            return new ArrayList<>();
        }
        try {
            String raw = new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8);
            if (raw.trim().isEmpty()) {
                return new ArrayList<>();
            }
            JsonNode root = MAPPER.readTree(raw);
            ArrayNode arr = JacksonTreeIO.asArray(root.get("logs"), MAPPER);
            ArrayList<LogEntry> list = new ArrayList<>();
            for (JsonNode n : arr) {
                list.add(MAPPER.treeToValue(n, LogEntry.class));
            }
            return list;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private static void writeLogEntries(ArrayList<LogEntry> entries) {
        try {
            Files.createDirectories(FILE.toPath().getParent());
            /* Map root avoids a separate Jackson container type (cleaner bytecode for tools / RMI class loading). */
            MAPPER.writeValue(FILE, Collections.singletonMap("logs", entries));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write log file", e);
        }
    }

    /** Log line DTO - public for Jackson (de)serialization. */
    public static class LogEntry {
        public String timestamp;
        public String user;
        public String action;
        public String details;
    }
}
