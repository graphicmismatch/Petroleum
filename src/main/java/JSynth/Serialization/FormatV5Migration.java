package JSynth.Serialization;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Map;

final class FormatV5Migration {
    private FormatV5Migration() {
    }

    static void apply(Map<String, Object> root, PatchArchive archive) {
        Object samples = root.remove("samples");
        if (samples != null) {
            for (Map.Entry<String, Object> entry : Json.asObject(samples).entrySet()) {
                archive.putSample(entry.getKey(), decodeSample(Json.asObject(entry.getValue())));
            }
        }
        migrateDocument(root, archive);
    }

    private static void migrateDocument(Map<String, Object> document, PatchArchive archive) {
        for (Object entry : Json.asArray(document.get("nodes"))) {
            Map<String, Object> json = Json.asObject(entry);
            String type = Json.string(json, "type");
            if (type.equals("Patch")) {
                migrateDocument(Json.asObject(json.get("patch")), archive);
            }
            Object stateValue = json.get("state");
            if (stateValue == null) {
                continue;
            }
            Map<String, Object> state = Json.asObject(stateValue);
            if (type.equals("Tracker")) {
                Object pattern = state.remove("pattern");
                Object length = state.remove("patternLength");
                if (pattern != null) {
                    byte[] raw = SampleCodec.inflate(Base64.getDecoder().decode(Json.asString(pattern)),
                            Integer.parseInt(Json.asString(length)));
                    json.put("song", archive.dataId(raw));
                }
            } else if (type.equals("MIDI Player")) {
                Object midi = state.remove("midi");
                Object file = state.remove("file");
                if (midi != null) {
                    json.put("midi", archive.dataId(Base64.getDecoder().decode(Json.asString(midi))));
                }
                if (file != null) {
                    json.put("file", Json.asString(file));
                }
            }
            if (state.isEmpty()) {
                json.remove("state");
            }
        }
    }

    private static float[] decodeSample(Map<String, Object> json) {
        int count = (int) Json.number(json, "count");
        float peak = (float) Json.number(json, "peak");
        byte[] pcm = SampleCodec.inflate(Base64.getDecoder().decode(Json.string(json, "data")), count * 2);
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        float[] table = new float[count];
        float scale = peak / 32767f;
        for (int i = 0; i < count; i++) {
            table[i] = buffer.getShort() * scale;
        }
        return table;
    }
}
