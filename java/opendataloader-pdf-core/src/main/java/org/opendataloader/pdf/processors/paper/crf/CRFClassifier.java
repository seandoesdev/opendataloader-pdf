package org.opendataloader.pdf.processors.paper.crf;

import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.Transducer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.SimpleTaggerSentence2TokenSequence;
import cc.mallet.pipe.TokenSequence2FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import org.opendataloader.pdf.paper.Zone;
import org.opendataloader.pdf.paper.ZoneType;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps MALLET's CRF for training and inference on zone sequences.
 */
public class CRFClassifier {

    public static final class Classification {
        private final ZoneType type;
        private final double confidence;

        public Classification(ZoneType type, double confidence) {
            this.type = type;
            this.confidence = confidence;
        }

        public ZoneType type() {
            return type;
        }

        public double confidence() {
            return confidence;
        }
    }

    public static final class LabeledZone {
        private final Zone zone;
        private final ZoneType label;

        public LabeledZone(Zone zone, ZoneType label) {
            this.zone = zone;
            this.label = label;
        }

        public Zone zone() {
            return zone;
        }

        public ZoneType label() {
            return label;
        }
    }

    private final CRFFeatureExtractor featureExtractor;
    private CRF crf;
    private Pipe pipe;

    public CRFClassifier() {
        this.featureExtractor = new CRFFeatureExtractor();
    }

    private CRFClassifier(CRF crf, Pipe pipe) {
        this.featureExtractor = new CRFFeatureExtractor();
        this.crf = crf;
        this.pipe = pipe;
    }

    public boolean isModelLoaded() {
        return crf != null;
    }

    public void train(List<List<LabeledZone>> trainingData) {
        pipe = buildPipe();
        InstanceList instances = new InstanceList(pipe);

        for (List<LabeledZone> document : trainingData) {
            String inputString = toTrainingString(document);
            Instance instance = new Instance(inputString, null, null, null);
            instances.addThruPipe(instance);
        }

        crf = new CRF(pipe, null);
        crf.addStatesForLabelsConnectedAsIn(instances);
        crf.addStartState();

        CRFTrainerByLabelLikelihood trainer = new CRFTrainerByLabelLikelihood(crf);
        trainer.setGaussianPriorVariance(10.0);

        for (int i = 0; i < 200; i++) {
            boolean converged = trainer.train(instances, 1);
            if (converged) {
                break;
            }
        }
    }

    public List<Classification> classify(List<Zone> zones) {
        if (!isModelLoaded()) {
            List<Classification> results = new ArrayList<>();
            for (int i = 0; i < zones.size(); i++) {
                results.add(new Classification(ZoneType.UNKNOWN, 0.0));
            }
            return results;
        }

        String inputString = toInferenceString(zones);
        Instance instance = new Instance(inputString, null, null, null);
        instance = pipe.instanceFrom(instance);

        Sequence<?> input = (Sequence<?>) instance.getData();
        Sequence<?> output = crf.transduce(input);

        List<Classification> results = new ArrayList<>();
        for (int i = 0; i < output.size(); i++) {
            String label = output.get(i).toString();
            ZoneType type;
            try {
                type = ZoneType.valueOf(label);
            } catch (IllegalArgumentException e) {
                type = ZoneType.UNKNOWN;
            }
            // Use a default confidence of 1.0 for CRF predictions since
            // MALLET's basic transduce doesn't expose per-position marginals easily
            results.add(new Classification(type, 1.0));
        }
        return results;
    }

    public void save(Path modelPath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(modelPath.toFile()))) {
            oos.writeObject(crf);
            oos.writeObject(pipe);
        }
    }

    public static CRFClassifier load(Path modelPath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(modelPath.toFile()))) {
            CRF crf = (CRF) ois.readObject();
            Pipe pipe = (Pipe) ois.readObject();
            return new CRFClassifier(crf, pipe);
        }
    }

    private Pipe buildPipe() {
        return new SerialPipes(new Pipe[]{
                new SimpleTaggerSentence2TokenSequence(),
                new TokenSequence2FeatureVectorSequence()
        });
    }

    private String toTrainingString(List<LabeledZone> document) {
        StringBuilder sb = new StringBuilder();
        for (LabeledZone lz : document) {
            String features = featureExtractor.toMalletFeatureString(lz.zone());
            sb.append(features).append(' ').append(lz.label().name()).append('\n');
        }
        return sb.toString();
    }

    private String toInferenceString(List<Zone> zones) {
        StringBuilder sb = new StringBuilder();
        for (Zone zone : zones) {
            sb.append(featureExtractor.toMalletFeatureString(zone)).append('\n');
        }
        return sb.toString();
    }
}
