package Petroleum.Modules;

import Petroleum.Graph.OneShot;
import Petroleum.Serialization.PatchDocument;

final class OneShotPatchNode extends PatchNode implements OneShot {

    OneShotPatchNode(String name, PatchDocument document) {
        super(name, document);
    }

    @Override
    public boolean isIdle() {
        return innerFinished();
    }
}
