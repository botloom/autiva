package cn.bitloom.learn.ml;

import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.dataset.Batch;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.translate.TranslateException;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The type Linear regression.
 *
 * @author bitloom
 */
public class LinearRegression {
    public static void main(String[] args) throws TranslateException, IOException {
        NDManager manager = NDManager.newBaseManager();

        NDArray trueW = manager.create(new float[]{2, -3.4f});
        float trueB = 4.2f;

        DataPoints dp = DataPoints.syntheticData(manager, trueW, trueB, 1000);
        NDArray features = dp.getX();
        NDArray labels = dp.getY();
        int batchSize = 10;
        ArrayDataset dataset = loadArray(features, labels, batchSize, false);

        Model model = Model.newInstance("lin-reg");

        SequentialBlock net = new SequentialBlock();
        Linear linearBlock = Linear.builder().optBias(true).setUnits(1).build();
        net.add(linearBlock);

        model.setBlock(net);
        Loss l2loss = Loss.l2Loss();
        Tracker lrt = Tracker.fixed(0.03f);
        Optimizer sgd = Optimizer.sgd().setLearningRateTracker(lrt).build();
        DefaultTrainingConfig config = new DefaultTrainingConfig(l2loss)
                .optOptimizer(sgd) // Optimizer (loss function)
                .optDevices(manager.getEngine().getDevices(1)) // single GPU
                .addTrainingListeners(TrainingListener.Defaults.logging()); // Logging

        Trainer trainer = model.newTrainer(config);
        trainer.initialize(new Shape(batchSize, 2));

        int numEpochs = 3;

        for (int epoch = 1; epoch <= numEpochs; epoch++) {
            System.out.printf("Epoch %d\n", epoch);
            // Iterate over dataset
            for (Batch batch : trainer.iterateDataset(dataset)) {
                // Update loss and evaulator
                EasyTrain.trainBatch(trainer, batch);

                // Update parameters
                trainer.step();

                batch.close();
            }
            // reset training and validation evaluators at end of epoch
            trainer.notifyListeners(listener -> listener.onEpoch(trainer));
        }
        Path modelDir = Paths.get("./models/lin-reg");
        Files.createDirectories(modelDir);

        model.setProperty("Epoch", Integer.toString(numEpochs)); // save epochs trained as metadata

        model.save(modelDir, "lin-reg");
    }

    public static ArrayDataset loadArray(NDArray features, NDArray labels, int batchSize, boolean shuffle) {
        return new ArrayDataset.Builder()
                .setData(features) // set the features
                .optLabels(labels) // set the labels
                .setSampling(batchSize, shuffle) // set the batch size and random sampling
                .build();
    }

    @Data
    public static class DataPoints {
        private NDArray X, y;

        public DataPoints(NDArray X, NDArray y) {
            this.X = X;
            this.y = y;
        }
        public static DataPoints syntheticData(NDManager manager, NDArray w, float b, int numExamples) {
            NDArray X = manager.randomNormal(new Shape(numExamples, w.size()));
            NDArray y = X.matMul(w).add(b);
            // Add noise
            y = y.add(manager.randomNormal(0, 0.01f, y.getShape(), DataType.FLOAT32));
            return new DataPoints(X, y);
        }
    }
}
