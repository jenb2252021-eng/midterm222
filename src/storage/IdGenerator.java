package storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates random 4-digit tree IDs and tracks used IDs to avoid collisions.
 * Metadata is persisted under {@link AppDataPaths#getDataDirectory()}.
 */
public final class IdGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final int MIN_ID = 1000;
    private static final int MAX_ID = 9999;
    private static final int ID_POOL_SIZE = (MAX_ID - MIN_ID) + 1;
    private static final int RANDOM_ATTEMPTS = 20_000;

    private static final Path DATA_DIR = AppDataPaths.getDataDirectory().toPath();
    private static final Path METADATA_FILE = DATA_DIR.resolve("id_metadata.json");
    private static final IdGenerator INSTANCE = new IdGenerator();

    private final SecureRandom random = new SecureRandom();
    private final Set<Integer> usedIds = new HashSet<>();

    private IdGenerator() {
        ensureDataDir();
        boolean changed = loadMetadata();
        changed |= seedFromDataFiles();
        if (changed) {
            persist();
        }
    }

    public static IdGenerator getInstance() {
        return INSTANCE;
    }

    public synchronized int nextId() {
        if (usedIds.size() >= ID_POOL_SIZE) {
            throw new IllegalStateException("No available 4-digit tree IDs remain.");
        }

        for (int i = 0; i < RANDOM_ATTEMPTS; i++) {
            int candidate = MIN_ID + random.nextInt(ID_POOL_SIZE);
            if (usedIds.add(candidate)) {
                persist();
                return candidate;
            }
        }

        // Deterministic fallback if random attempts collide repeatedly.
        for (int candidate = MIN_ID; candidate <= MAX_ID; candidate++) {
            if (usedIds.add(candidate)) {
                persist();
                return candidate;
            }
        }

        throw new IllegalStateException("No available 4-digit tree IDs remain.");
    }

    public synchronized void syncWithId(int candidate) {
        if (addIfInRange(candidate)) {
            persist();
        }
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create data directory for ID metadata", e);
        }
    }

    private boolean loadMetadata() {
        if (!Files.exists(METADATA_FILE)) {
            return false;
        }
        try {
            String raw = Files.readString(METADATA_FILE);
            if (raw == null || raw.trim().isEmpty()) {
                return false;
            }
            JsonNode json = MAPPER.readTree(raw);
            JsonNode ids = json.path("usedIds");
            if (!ids.isArray()) {
                return false;
            }
            boolean changed = false;
            for (JsonNode node : ids) {
                changed |= addIfInRange(node.asInt(-1));
            }
            return changed;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean seedFromDataFiles() {
        boolean changed = false;
        changed |= collectIdsFromFile(AppDataPaths.dataFile("tree_data.json").toPath(), "trees");
        changed |= collectIdsFromFile(AppDataPaths.dataFile("pending_tree_data.json").toPath(), "pendingTrees");
        changed |= collectIdsFromFile(AppDataPaths.dataFile("deleted_tree_data.json").toPath(), "deletedTrees");
        return changed;
    }

    private boolean collectIdsFromFile(Path file, String arrayField) {
        if (!Files.exists(file)) {
            return false;
        }
        try {
            String raw = Files.readString(file);
            if (raw == null || raw.trim().isEmpty()) {
                return false;
            }
            JsonNode root = MAPPER.readTree(raw);
            JsonNode array = root.path(arrayField);
            if (!array.isArray()) {
                return false;
            }
            boolean changed = false;
            for (JsonNode node : array) {
                if (node != null && node.isObject()) {
                    changed |= addIfInRange(node.path("id").asInt(-1));
                }
            }
            return changed;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean addIfInRange(int candidate) {
        if (candidate < MIN_ID || candidate > MAX_ID) {
            return false;
        }
        return usedIds.add(candidate);
    }

    private void persist() {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("format", "random-4-digit");
        json.put("minId", MIN_ID);
        json.put("maxId", MAX_ID);
        ArrayNode ids = json.putArray("usedIds");
        usedIds.stream().sorted().forEach(ids::add);
        try {
            MAPPER.writeValue(METADATA_FILE.toFile(), json);
        } catch (IOException e) {
            System.err.println("Unable to persist tree ID metadata: " + e.getMessage());
        }
    }
}
