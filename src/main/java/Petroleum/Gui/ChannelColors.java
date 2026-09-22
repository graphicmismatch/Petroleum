package Petroleum.Gui;

import javafx.scene.paint.Color;

final class ChannelColors {
    private ChannelColors() {
    }

    static Color fill(int channel) {
        return Color.hsb((channel * 45) % 360, 0.55, 0.85);
    }
}
