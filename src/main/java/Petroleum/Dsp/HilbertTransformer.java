package Petroleum.Dsp;

public class HilbertTransformer {
    private static final float[] COEFFS_IN_PHASE = {
            0.16175849836770f, 0.73302893234149f, 0.94534970032911f, 0.99059915668453f
    };
    private static final float[] COEFFS_QUADRATURE = {
            0.47940086558884f, 0.87621849353931f, 0.97659758950819f, 0.99749925593555f
    };

    private final float[][] stagesXInPhase = new float[COEFFS_IN_PHASE.length][2];
    private final float[][] stagesYInPhase = new float[COEFFS_IN_PHASE.length][2];
    private final float[][] stagesXQuadrature = new float[COEFFS_QUADRATURE.length][2];
    private final float[][] stagesYQuadrature = new float[COEFFS_QUADRATURE.length][2];
    private float quadratureDelay;
    private int parity;

    public void process(float input, float[] outInPhaseAndQuadrature) {
        outInPhaseAndQuadrature[0] = cascade(input, COEFFS_IN_PHASE, stagesXInPhase, stagesYInPhase, parity);

        float quadratureRaw = cascade(input, COEFFS_QUADRATURE, stagesXQuadrature, stagesYQuadrature, parity);
        outInPhaseAndQuadrature[1] = quadratureDelay;
        quadratureDelay = quadratureRaw;

        parity ^= 1;
    }

    private static float cascade(float input, float[] coeffs, float[][] xHistory, float[][] yHistory, int parity) {
        float x = input;
        for (int i = 0; i < coeffs.length; i++) {
            float coeff = coeffs[i];
            float x2 = xHistory[i][parity];
            float y2 = yHistory[i][parity];
            float y = coeff * (x + y2) - x2;
            xHistory[i][parity] = x;
            yHistory[i][parity] = y;
            x = y;
        }
        return x;
    }
}
