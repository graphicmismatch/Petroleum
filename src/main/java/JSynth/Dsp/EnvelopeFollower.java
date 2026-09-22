package JSynth.Dsp;

public class EnvelopeFollower {
    private double cachedAttackMs = Double.NaN;
    private double cachedReleaseMs = Double.NaN;
    private double cachedSampleRate = -1;
    private double attackCoeff;
    private double releaseCoeff;

    public void updateCoefficients(double attackMs, double releaseMs, double sampleRate) {
        if (attackMs != cachedAttackMs || releaseMs != cachedReleaseMs || sampleRate != cachedSampleRate) {
            attackCoeff = coeff(attackMs, sampleRate);
            releaseCoeff = coeff(releaseMs, sampleRate);
            cachedAttackMs = attackMs;
            cachedReleaseMs = releaseMs;
            cachedSampleRate = sampleRate;
        }
    }

    public void updateReleaseCoefficient(double releaseMs, double sampleRate) {
        if (releaseMs != cachedReleaseMs || sampleRate != cachedSampleRate) {
            releaseCoeff = coeff(releaseMs, sampleRate);
            cachedReleaseMs = releaseMs;
            cachedSampleRate = sampleRate;
        }
    }

    private static double coeff(double timeMs, double sampleRate) {
        return Math.exp(-1.0 / (sampleRate * timeMs / 1000.0));
    }

    public double getAttackCoeff() {
        return attackCoeff;
    }

    public double getReleaseCoeff() {
        return releaseCoeff;
    }

    public double followAttackRelease(double currentValue, double targetValue) {
        double coeff = targetValue > currentValue ? attackCoeff : releaseCoeff;
        return coeff * currentValue + (1 - coeff) * targetValue;
    }

    public double followInstantAttack(double currentValue, double targetValue) {
        if (targetValue > currentValue) {
            return targetValue;
        }
        return releaseCoeff * currentValue + (1 - releaseCoeff) * targetValue;
    }
}
