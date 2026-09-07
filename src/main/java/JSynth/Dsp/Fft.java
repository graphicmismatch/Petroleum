package JSynth.Dsp;

public final class Fft {
    private final int size;
    private final int[] bitReversal;
    private final float[] cosTable;
    private final float[] sinTable;

    public Fft(int size) {
        if (size < 2 || (size & (size - 1)) != 0) {
            throw new IllegalArgumentException("Fft size must be a power of 2 (got " + size + ")");
        }
        this.size = size;

        int bits = Integer.numberOfTrailingZeros(size);
        bitReversal = new int[size];
        for (int i = 0; i < size; i++) {
            int reversed = 0;
            int v = i;
            for (int b = 0; b < bits; b++) {
                reversed = (reversed << 1) | (v & 1);
                v >>= 1;
            }
            bitReversal[i] = reversed;
        }

        int half = size / 2;
        cosTable = new float[half];
        sinTable = new float[half];
        for (int k = 0; k < half; k++) {
            double angle = 2 * Math.PI * k / size;
            cosTable[k] = (float) Math.cos(angle);
            sinTable[k] = (float) Math.sin(angle);
        }
    }

    public int size() {
        return size;
    }

    public void forward(float[] re, float[] im) {
        checkLength(re);
        checkLength(im);

        for (int i = 0; i < size; i++) {
            int j = bitReversal[i];
            if (i < j) {
                float tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                float ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }

        for (int len = 2; len <= size; len <<= 1) {
            int half = len / 2;
            int tableStep = size / len;
            for (int i = 0; i < size; i += len) {
                int twIdx = 0;
                for (int j = 0; j < half; j++) {
                    int idxA = i + j;
                    int idxB = idxA + half;

                    float wr = cosTable[twIdx];
                    float wi = -sinTable[twIdx];

                    float br = re[idxB];
                    float bi = im[idxB];
                    float tr = br * wr - bi * wi;
                    float ti = br * wi + bi * wr;

                    float ar = re[idxA];
                    float ai = im[idxA];

                    re[idxA] = ar + tr;
                    im[idxA] = ai + ti;
                    re[idxB] = ar - tr;
                    im[idxB] = ai - ti;

                    twIdx += tableStep;
                }
            }
        }
    }

    public void inverse(float[] re, float[] im) {
        checkLength(re);
        checkLength(im);

        for (int i = 0; i < size; i++) {
            im[i] = -im[i];
        }

        forward(re, im);

        float invSize = 1f / size;
        for (int i = 0; i < size; i++) {
            re[i] = re[i] * invSize;
            im[i] = -im[i] * invSize;
        }
    }

    private void checkLength(float[] array) {
        if (array.length != size) {
            throw new IllegalArgumentException(
                    "Expected array of length " + size + " but got " + array.length);
        }
    }
}
