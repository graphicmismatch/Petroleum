package JSynth.Generators;

public enum SoundCategory {
    PICKUP("Pickup"),
    LASER("Laser"),
    EXPLOSION("Explosion"),
    POWERUP("Powerup"),
    HIT("Hit"),
    JUMP("Jump"),
    BLIP("Blip"),
    RANDOM("Random");

    private final String label;

    SoundCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
