package JSynth.Serialization;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class SamplePool {
    private static final int CHUNK = 8192;

    private final Map<String, float[]> byId = new LinkedHashMap<>();
    private final Map<Integer, List<String>> byHash = new HashMap<>();
    private int next;

    String idFor(float[] table) {
        int hash = Arrays.hashCode(table);
        List<String> candidates = byHash.computeIfAbsent(hash, key -> new ArrayList<>());
        for (String id : candidates) {
            if (Arrays.equals(byId.get(id), table)) {
                return id;
            }
        }
        String id = "s" + next++;
        byId.put(id, table);
        candidates.add(id);
        return id;
    }

    float[] require(String id) {
        float[] table = byId.get(id);
        if (table == null) {
            throw new SerializationException("Patch refers to sample '" + id + "', which is not in the file");
        }
        return table;
    }

    boolean isEmpty() {
        return byId.isEmpty();
    }

    Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        for (Map.Entry<String, float[]> entry : byId.entrySet()) {
            json.put(entry.getKey(), encode(entry.getValue()));
        }
        return json;
    }

    void readJson(Object value) {
        if (value == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : Json.asObject(value).entrySet()) {
            byId.put(entry.getKey(), decode(Json.asObject(entry.getValue())));
        }
    }

    private static Map<String, Object> encode(float[] table) {
        float peak = 0f;
        for (float sample : table) {
            peak = Math.max(peak, Math.abs(sample));
        }
        byte[] pcm = new byte[table.length * 2];
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        float scale = peak == 0f ? 0f : 32767f / peak;
        for (float sample : table) {
            buffer.putShort((short) Math.round(sample * scale));
        }

        Map<String, Object> json = Json.object();
        json.put("count", table.length);
        json.put("peak", peak);
        json.put("data", Base64.getEncoder().encodeToString(deflate(pcm)));
        return json;
    }

    private static float[] decode(Map<String, Object> json) {
        int count = (int) Json.number(json, "count");
        float peak = (float) Json.number(json, "peak");
        byte[] pcm = inflate(Base64.getDecoder().decode(Json.string(json, "data")), count * 2);
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        float[] table = new float[count];
        float scale = peak / 32767f;
        for (int i = 0; i < count; i++) {
            table[i] = buffer.getShort() * scale;
        }
        return table;
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2 + 64);
        byte[] chunk = new byte[CHUNK];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] compressed, int expectedLength) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] raw = new byte[expectedLength];
        try {
            int written = 0;
            while (written < expectedLength && !inflater.finished()) {
                int produced = inflater.inflate(raw, written, expectedLength - written);
                if (produced == 0 && inflater.needsInput()) {
                    throw new SerializationException("Sample data is truncated");
                }
                written += produced;
            }
            if (written < expectedLength) {
                throw new SerializationException("Sample data is truncated");
            }
        } catch (DataFormatException ex) {
            throw new SerializationException("Sample data is corrupt", ex);
        } finally {
            inflater.end();
        }
        return raw;
    }
}
