package Petroleum.Gui;

import javafx.scene.shape.Circle;

public final class PortHandle extends Circle {
    private final NodeView owner;
    private final String portName;
    private final boolean output;

    public PortHandle(NodeView owner, String portName, boolean output) {
        super(6);
        this.owner = owner;
        this.portName = portName;
        this.output = output;
        setFill(output ? Theme.PORT_OUTPUT : Theme.PORT_INPUT);
        setStroke(Theme.PORT_STROKE);
        setStrokeWidth(1);
    }

    public NodeView getOwner() {
        return owner;
    }

    public String getPortName() {
        return portName;
    }

    public boolean isOutput() {
        return output;
    }
}
