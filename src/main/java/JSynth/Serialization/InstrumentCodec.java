package JSynth.Serialization;

import JSynth.Instruments.Instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InstrumentCodec {
    public Map<String, Object> save(Instrument instrument, PatchArchive samples) {
        Map<String, Object> json = Json.object();

        List<Object> zones = new ArrayList<>();
        for (Instrument.SampleZone zone : instrument.zones()) {
            Map<String, Object> zoneJson = Json.object();
            zoneJson.put("name", zone.name);
            zoneJson.put("sample", samples.sampleId(zone.samples));
            zoneJson.put("rootHz", zone.rootHz);
            zoneJson.put("minHz", zone.minHz);
            zoneJson.put("maxHz", zone.maxHz);
            zoneJson.put("loopEnabled", zone.loopEnabled);
            zoneJson.put("loopStart", zone.loopStartFrame);
            zoneJson.put("loopEnd", zone.loopEndFrame);
            zones.add(zoneJson);
        }
        json.put("zones", zones);

        List<Object> envelope = new ArrayList<>();
        for (Instrument.EnvelopePoint point : instrument.envelope()) {
            Map<String, Object> pointJson = Json.object();
            pointJson.put("time", point.timeSeconds());
            pointJson.put("level", point.level());
            envelope.add(pointJson);
        }
        json.put("envelope", envelope);
        json.put("sustainPoint", instrument.sustainPointIndex());

        return json;
    }

    public Instrument load(Map<String, Object> json, PatchArchive samples) {
        Instrument instrument = new Instrument();

        for (Object entry : Json.asArray(json.get("zones"))) {
            Map<String, Object> zoneJson = Json.asObject(entry);
            Instrument.SampleZone zone = new Instrument.SampleZone(
                    Json.stringOrDefault(zoneJson, "name", "Sample"),
                    samples.sample(Json.string(zoneJson, "sample")),
                    (float) Json.number(zoneJson, "rootHz"),
                    (float) Json.number(zoneJson, "minHz"),
                    (float) Json.number(zoneJson, "maxHz"));
            zone.loopEnabled = Boolean.TRUE.equals(zoneJson.get("loopEnabled"));
            zone.loopStartFrame = (int) Json.numberOrDefault(zoneJson, "loopStart", 0);
            zone.loopEndFrame = (int) Json.numberOrDefault(zoneJson, "loopEnd", zone.samples.length);
            instrument.zones().add(zone);
        }

        List<Instrument.EnvelopePoint> envelope = new ArrayList<>();
        for (Object entry : Json.asArray(json.get("envelope"))) {
            Map<String, Object> pointJson = Json.asObject(entry);
            envelope.add(new Instrument.EnvelopePoint(
                    (float) Json.number(pointJson, "time"), (float) Json.number(pointJson, "level")));
        }
        if (!envelope.isEmpty()) {
            instrument.setEnvelope(envelope);
        }
        instrument.setSustainPointIndex((int) Json.numberOrDefault(json, "sustainPoint", -1));

        return instrument;
    }
}
