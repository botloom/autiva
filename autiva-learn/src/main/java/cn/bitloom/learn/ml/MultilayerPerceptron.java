package cn.bitloom.learn.ml;

import ai.djl.Model;
import ai.djl.basicdataset.cv.classification.Mnist;
import ai.djl.basicmodelzoo.basic.Mlp;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type Perceptron.
 *
 * @author bitloom
 */
public class MultilayerPerceptron {
    public static void main(String[] args) throws IOException, TranslateException {
        /*
         *数据集
         */
        int batchSize = 32;
        Mnist mnist = Mnist.builder().setSampling(batchSize, true).build();
        mnist.prepare(new ProgressBar());
        /*
         * 模型
         */
        Model model = Model.newInstance("MultilayerPerceptron");
        //三层感知机
        model.setBlock(new Mlp(28 * 28, 10, new int[] {128, 64}));
        /*
         * 训练器
         */
        DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())//损失函数
                .addEvaluator(new Accuracy())
                .addTrainingListeners(TrainingListener.Defaults.logging());
        Trainer trainer = model.newTrainer(config);
        trainer.initialize(new Shape(1, 28 * 28));
        /*
         * 训练模型
         */
        int epoch = 2;
        EasyTrain.fit(trainer, epoch, mnist, null);
        /*
         * 使用模型
         */
        Image image = ImageFactory.getInstance().fromUrl("https://resources.djl.ai/images/0.png");
        image.getWrappedImage();
        //输入输出转换
        Translator<Image, Classifications> translator = new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, Image input) {
                NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.GRAYSCALE);
                return new NDList(NDImageUtils.toTensor(array));
            }
            @Override
            public Classifications processOutput(TranslatorContext ctx, NDList list) {
                NDArray probabilities = list.singletonOrThrow().softmax(0);
                List<String> classNames = IntStream.range(0, 10).mapToObj(String::valueOf).collect(Collectors.toList());
                return new Classifications(classNames, probabilities);
            }
            @Override
            public Batchifier getBatchifier() {
                return Translator.super.getBatchifier();
            }
        };
        //预测
        Predictor<Image, Classifications> predictor = model.newPredictor(translator);
        //结果
        Classifications predict = predictor.predict(image);
        System.out.println(predict);
    }
}
