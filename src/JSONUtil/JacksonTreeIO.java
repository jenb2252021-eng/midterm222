package JSONUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Shared Jackson {@link ObjectMapper} for JSON files (trees, users, chat, logs, etc.).
 */
public final class JacksonTreeIO {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JacksonTreeIO() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Matches legacy lenient parsing: a missing node is an empty array; a single object is wrapped as one element.
     */
    public static ArrayNode asArray(JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isNull()) {
            return mapper.createArrayNode();
        }
        if (node.isArray()) {
            return (ArrayNode) node;
        }
        if (node.isObject()) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(node);
            return arr;
        }
        return mapper.createArrayNode();
    }

    public static <T> T read(File file, Class<T> type) throws IOException {
        if (!file.exists()) {
            return MAPPER.readValue("{}", type);
        }
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) {
            return MAPPER.readValue("{}", type);
        }
        return MAPPER.readValue(file, type);
    }

    public static void write(File file, Object value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        MAPPER.writeValue(file, value);
    }
}
