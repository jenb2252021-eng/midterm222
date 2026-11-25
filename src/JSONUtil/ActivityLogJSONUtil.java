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
