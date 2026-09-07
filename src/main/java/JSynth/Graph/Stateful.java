package JSynth.Graph;

import java.util.Map;

public interface Stateful {
    void saveState(Map<String, String> state);

    void loadState(Map<String, String> state);
}
