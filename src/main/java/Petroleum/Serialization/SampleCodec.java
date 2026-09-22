package Petroleum.Serialization;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class SampleCodec {
    private static final byte METHOD_INT_PLANES = 0;
    private static final byte METHOD_INT_RICE = 1;
    private static final byte METHOD_FLOAT_PLANES = 2;

    private static final int MAX_SHIFT = 24;
    private static final int RICE_BLOCK = 4096;
    private static final int RICE_ESCAPE = 48;
    private static final int HEADER_BYTES = 7;
    private static final int CHUNK = 65536;

    private SampleCodec() {
    }

    static byte[] encode(float[] table) {
        int shift = integerShift(table);
        if (shift < 0) {
            int[] bits = new int[table.length];
            int previous = 0;
            for (int i = 0; i < table.length; i++) {
                int raw = Float.floatToRawIntBits(table[i]);
                bits[i] = raw ^ previous;
                previous = raw;
            }
            return withHeader(METHOD_FLOAT_PLANES, table.length, 0, 4, deflate(planes(bits, 4)));
        }

        float scale = (float) (1 << shift);
        int[] values = new int[table.length];
        for (int i = 0; i < table.length; i++) {
            values[i] = (int) (table[i] * scale);
        }
        int width = byteWidth(values);
        byte[] planar = withHeader(METHOD_INT_PLANES, table.length, shift, width, deflate(planes(values, width)));
        byte[] rice = withHeader(METHOD_INT_RICE, table.length, shift, 0, rice(values));
        return rice.length < planar.length ? rice : planar;
    }

    static float[] decode(byte[] data) {
        if (data.length < HEADER_BYTES) {
            throw new SerializationException("Patch data is truncated");
        }
        ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte method = header.get();
        int count = header.getInt();
        int shift = header.get();
        int width = header.get();
        if (count < 0 || (long) count * 4 > Integer.MAX_VALUE - 8 || shift < 0 || shift > MAX_SHIFT || width < 0 || width > 4) {
            throw new SerializationException("Sample data is corrupt");
        }
        byte[] payload = Arrays.copyOfRange(data, HEADER_BYTES, data.length);

        float[] table = new float[count];
        switch (method) {
            case METHOD_FLOAT_PLANES -> {
                int[] bits = unplanes(inflate(payload, count * 4), count, 4);
                int previous = 0;
                for (int i = 0; i < count; i++) {
                    previous ^= bits[i];
                    table[i] = Float.intBitsToFloat(previous);
                }
            }
            case METHOD_INT_PLANES, METHOD_INT_RICE -> {
                int[] values = method == METHOD_INT_RICE
                        ? unrice(payload, count)
                        : unplanes(inflate(payload, count * width), count, width);
                float scale = (float) (1 << shift);
                for (int i = 0; i < count; i++) {
                    table[i] = values[i] / scale;
                }
            }
            default -> throw new SerializationException("Unknown sample encoding " + method);
        }
        return table;
    }

    private static int integerShift(float[] table) {
        for (int shift = 0; shift <= MAX_SHIFT; shift++) {
            float scale = (float) (1 << shift);
            boolean exact = true;
            for (float sample : table) {
                float scaled = sample * scale;
                if (scaled != (int) scaled || Math.abs(scaled) > (1 << MAX_SHIFT)
                        || Float.floatToRawIntBits(sample) == 0x80000000) {
                    exact = false;
                    break;
                }
            }
            if (exact) {
                return shift;
            }
        }
        return -1;
    }

    private static int byteWidth(int[] values) {
        int width = 1;
        for (int value : values) {
            while (width < 4 && (value < -(1 << (8 * width - 1)) || value >= (1 << (8 * width - 1)))) {
                width++;
            }
        }
        return width;
    }

    private static byte[] withHeader(byte method, int count, int shift, int width, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(method).putInt(count).put((byte) shift).put((byte) width).put(payload);
        return buffer.array();
    }

    private static byte[] planes(int[] values, int width) {
        int n = values.length;
        byte[] out = new byte[n * width];
        for (int plane = 0; plane < width; plane++) {
            int offset = plane * n;
            int bitShift = plane * 8;
            for (int i = 0; i < n; i++) {
                out[offset + i] = (byte) (values[i] >> bitShift);
            }
        }
        return out;
    }

    private static int[] unplanes(byte[] bytes, int n, int width) {
        int[] values = new int[n];
        for (int plane = 0; plane < width; plane++) {
            int offset = plane * n;
            int bitShift = plane * 8;
            for (int i = 0; i < n; i++) {
                values[i] |= (bytes[offset + i] & 0xFF) << bitShift;
            }
        }
        int signShift = 32 - 8 * width;
        for (int i = 0; i < n; i++) {
            values[i] = (values[i] << signShift) >> signShift;
        }
        return values;
    }

    private static long residual(int[] x, int i, int order) {
        long a = x[i];
        long b = i > 0 ? x[i - 1] : 0;
        long c = i > 1 ? x[i - 2] : 0;
        long d = i > 2 ? x[i - 3] : 0;
        return switch (order) {
            case 0 -> a;
            case 1 -> a - b;
            case 2 -> a - 2 * b + c;
            default -> a - 3 * b + 3 * c - d;
        };
    }

    private static long prediction(int[] x, int i, int order) {
        long b = i > 0 ? x[i - 1] : 0;
        long c = i > 1 ? x[i - 2] : 0;
        long d = i > 2 ? x[i - 3] : 0;
        return switch (order) {
            case 0 -> 0;
            case 1 -> b;
            case 2 -> 2 * b - c;
            default -> 3 * b - 3 * c + d;
        };
    }

    private static byte[] rice(int[] x) {
        BitWriter out = new BitWriter(x.length);
        for (int start = 0; start < x.length; start += RICE_BLOCK) {
            int end = Math.min(x.length, start + RICE_BLOCK);
            int bestOrder = 0;
            long bestSum = Long.MAX_VALUE;
            for (int order = 0; order <= 3; order++) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += Math.abs(residual(x, i, order));
                }
                if (sum < bestSum) {
                    bestSum = sum;
                    bestOrder = order;
                }
            }
            long mean = bestSum / (end - start);
            int k = 0;
            while (k < 30 && (1L << (k + 1)) <= mean) {
                k++;
            }
            out.write(bestOrder, 2);
            out.write(k, 5);
            for (int i = start; i < end; i++) {
                long value = residual(x, i, bestOrder);
                long zigzag = (value << 1) ^ (value >> 63);
                long quotient = zigzag >>> k;
                if (quotient < RICE_ESCAPE) {
                    out.zeros((int) quotient);
                    out.write(1, 1);
                    out.write(zigzag, k);
                } else {
                    out.zeros(RICE_ESCAPE);
                    out.write(zigzag, 32);
                }
            }
        }
        return out.toByteArray();
    }

    private static int[] unrice(byte[] data, int count) {
        BitReader in = new BitReader(data);
        int[] x = new int[count];
        for (int start = 0; start < count; start += RICE_BLOCK) {
            int end = Math.min(count, start + RICE_BLOCK);
            int order = (int) in.read(2);
            int k = (int) in.read(5);
            for (int i = start; i < end; i++) {
                int quotient = 0;
                while (quotient < RICE_ESCAPE && in.read(1) == 0) {
                    quotient++;
                }
                long zigzag = quotient < RICE_ESCAPE ? ((long) quotient << k) | in.read(k) : in.read(32);
                long value = (zigzag >>> 1) ^ -(zigzag & 1);
                x[i] = (int) (prediction(x, i, order) + value);
            }
        }
        return x;
    }

    private static final class BitWriter {
        private byte[] buffer;
        private int position;
        private long accumulator;
        private int pending;

        BitWriter(int sampleCount) {
            buffer = new byte[Math.max(64, sampleCount)];
        }

        void write(long value, int bits) {
            if (bits == 0) {
                return;
            }
            if (bits > 32) {
                write(value >>> 32, bits - 32);
                bits = 32;
            }
            accumulator = (accumulator << bits) | (value & ((1L << bits) - 1));
            pending += bits;
            while (pending >= 8) {
                pending -= 8;
                if (position == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                buffer[position++] = (byte) (accumulator >>> pending);
            }
        }

        void zeros(int count) {
            while (count > 32) {
                write(0, 32);
                count -= 32;
            }
            write(0, count);
        }

        byte[] toByteArray() {
            if (pending > 0) {
                write(0, 8 - pending);
            }
            return Arrays.copyOf(buffer, position);
        }
    }

    private static final class BitReader {
        private final byte[] data;
        private int position;
        private long accumulator;
        private int available;

        BitReader(byte[] data) {
            this.data = data;
        }

        long read(int bits) {
            if (bits == 0) {
                return 0;
            }
            while (available < bits) {
                if (position >= data.length) {
                    throw new SerializationException("Patch data is truncated");
                }
                accumulator = (accumulator << 8) | (data[position++] & 0xFF);
                available += 8;
            }
            available -= bits;
            return (accumulator >>> available) & ((1L << bits) - 1);
        }
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
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

    static byte[] inflate(byte[] compressed, int expectedLength) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] raw = new byte[expectedLength];
        try {
            int written = 0;
            while (written < expectedLength && !inflater.finished()) {
                int produced = inflater.inflate(raw, written, expectedLength - written);
                if (produced == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new SerializationException("Patch data is truncated");
                }
                written += produced;
            }
            if (written < expectedLength) {
                throw new SerializationException("Patch data is truncated");
            }
        } catch (DataFormatException ex) {
            throw new SerializationException("Patch data is corrupt", ex);
        } finally {
            inflater.end();
        }
        return raw;
    }
}
