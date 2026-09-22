package Petroleum.Gui;

import javafx.scene.paint.Color;

final class Theme {
    static final Color CANVAS = Color.web("#1c1e22");
    static final Color NODE_BODY = Color.web("#2b2f36");
    static final Color NODE_HEADER = Color.web("#3a3f48");
    static final Color BORDER = Color.web("#4a4f58");
    static final Color ACCENT = Color.web("#7fd0ff");
    static final Color WARNING = Color.web("#e0a35a");

    static final Color TEXT = Color.web("#d8dce2");
    static final Color TEXT_STRONG = Color.web("#f0f0f0");
    static final Color TEXT_DIM = Color.web("#8a93a0");
    static final Color TEXT_FAINT = Color.web("#6a7280");

    static final Color WAVE_BACKGROUND = Color.web("#15171a");
    static final Color WAVE_MIDLINE = Color.web("#3a3f48");
    static final Color WAVE_TRACE = ACCENT;

    static final Color WIRE = ACCENT;
    static final Color WIRE_SELECTED = Color.web("#ffe066");

    static final Color PORT_INPUT = Color.web("#ffb84d");
    static final Color PORT_OUTPUT = Color.web("#4da6ff");
    static final Color PORT_STROKE = Color.web("#20242a");

    static final double GAP = 8;
    static final double PAD = 12;
    static final double SECTION_GAP = 16;
    static final double PANEL_WIDTH = 280;

    static final double FONT_SMALL = 11;
    static final double FONT_BODY = 13;
    static final double FONT_HEADING = 14;
    static final double FONT_TITLE = 20;

    private Theme() {
    }

    static String formatValue(float value) {
        return Math.abs(value) >= 100 ? String.format("%.0f", value) : String.format("%.3f", value);
    }

    static String hex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
