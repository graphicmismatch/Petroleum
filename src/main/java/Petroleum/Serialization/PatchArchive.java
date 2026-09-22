package Petroleum.Serialization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class PatchArchive {
    static final String DOCUMENT_ENTRY = "patch.json";
    private static final String SAMPLE_PREFIX = "samples/";
    private static final String DATA_PREFIX = "data/";
    private static final long ENTRY_TIME = 315532800000L;

    private final Map<String, float[]> samples = new LinkedHashMap<>();
    private final Map<Integer, List<String>> samplesByHash = new HashMap<>();
    private final Map<String, byte[]> data = new LinkedHashMap<>();
    private int nextSample;
    private int nextData;
    private String document;

    static boolean isArchive(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }

    String sampleId(float[] table) {
        int hash = Arrays.hashCode(table);
        List<String> candidates = samplesByHash.computeIfAbsent(hash, key -> new ArrayList<>());
        for (String id : candidates) {
            if (Arrays.equals(samples.get(id), table)) {
                return id;
            }
        }
        String id = "s" + nextSample++;
        samples.put(id, table);
        candidates.add(id);
        return id;
    }

    float[] sample(String id) {
        float[] table = samples.get(id);
        if (table == null) {
            throw new SerializationException("Patch refers to sample '" + id + "', which is not in the file");
        }
        return table;
    }

    void putSample(String id, float[] table) {
        samples.put(id, table);
    }

    String dataId(byte[] bytes) {
        String id = "d" + nextData++;
        data.put(id, bytes);
        return id;
    }

    byte[] data(String id) {
        byte[] bytes = data.get(id);
        if (bytes == null) {
            throw new SerializationException("Patch refers to data '" + id + "', which is not in the file");
        }
        return bytes;
    }

    String document() {
        if (document == null) {
            throw new SerializationException("Archive has no " + DOCUMENT_ENTRY);
        }
        return document;
    }

    void write(OutputStream out, String json) throws IOException {
        List<Map.Entry<String, byte[]>> encoded = samples.entrySet().parallelStream()
                .map(entry -> Map.entry(entry.getKey(), SampleCodec.encode(entry.getValue())))
                .toList();

        ZipOutputStream zip = new ZipOutputStream(out);
        zip.setLevel(Deflater.DEFAULT_COMPRESSION);
        writeDeflated(zip, DOCUMENT_ENTRY, json.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> entry : encoded) {
            writeStored(zip, SAMPLE_PREFIX + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            writeDeflated(zip, DATA_PREFIX + entry.getKey(), entry.getValue());
        }
        zip.finish();
        zip.flush();
    }

    static PatchArchive read(byte[] bytes) {
        PatchArchive archive = new PatchArchive();
        Map<String, byte[]> encodedSamples = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals(DOCUMENT_ENTRY)) {
                    archive.document = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                } else if (name.startsWith(SAMPLE_PREFIX)) {
                    encodedSamples.put(name.substring(SAMPLE_PREFIX.length()), zip.readAllBytes());
                } else if (name.startsWith(DATA_PREFIX)) {
                    archive.data.put(name.substring(DATA_PREFIX.length()), zip.readAllBytes());
                }
            }
        } catch (IOException ex) {
            throw new SerializationException("Patch archive is corrupt: " + ex.getMessage(), ex);
        }
        encodedSamples.entrySet().parallelStream()
                .map(entry -> Map.entry(entry.getKey(), SampleCodec.decode(entry.getValue())))
                .toList()
                .forEach(entry -> archive.samples.put(entry.getKey(), entry.getValue()));
        return archive;
    }

    private static void writeDeflated(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ENTRY_TIME);
        entry.setMethod(ZipEntry.DEFLATED);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void writeStored(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ENTRY_TIME);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
