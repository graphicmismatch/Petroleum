package JSynth.Modules;

import JSynth.Graph.OneShot;
import JSynth.Serialization.PatchDocument;

final class OneShotPatchNode extends PatchNode implements OneShot {

    OneShotPatchNode(String name, PatchDocument document) {
        super(name, document);
    }

    @Override
    public boolean isIdle() {
        return innerFinished();
    }
}
