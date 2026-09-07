package JSynth.Gui;

import JSynth.Serialization.Macro;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;

import java.util.function.BooleanSupplier;

final class MacroSliderRow {
    private final Macro macro;
    private final Slider slider;
    private final Label readout;

    MacroSliderRow(Macro macro, Runnable onChange, BooleanSupplier suppressed) {
        this.macro = macro;
        this.readout = new Label(Theme.formatValue(macro.target().getValue()));
        this.readout.setTextFill(Theme.TEXT_DIM);
        this.slider = new Slider(macro.min(), macro.max(), macro.target().getValue());
        this.slider.setMaxWidth(Double.MAX_VALUE);
        this.slider.valueProperty().addListener((obs, oldV, newV) -> {
            if (suppressed.getAsBoolean()) {
                return;
            }
            macro.target().setValue(newV.floatValue());
            this.readout.setText(Theme.formatValue(newV.floatValue()));
            onChange.run();
        });
    }

    Macro macro() {
        return macro;
    }

    Slider slider() {
        return slider;
    }

    Label readout() {
        return readout;
    }

    void syncFromModel() {
        float value = macro.target().getValue();
        slider.setValue(value);
        readout.setText(Theme.formatValue(value));
    }
}
