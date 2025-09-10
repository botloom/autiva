package cn.bitloom.learn.ml;

import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.Plot;
import tech.tablesaw.plotly.api.LinePlot;

/**
 * The type Linear regression.
 *
 * @author bitloom
 */
public class LinearRegression {
    public static void main(String[] args) {
        // 构造数据
        int n = 100;
        double[] x = new double[n];
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            x[i] = i - 50;        // x 从 -50 到 49
            y[i] = x[i] * x[i];   // y = x^2
        }

        // 转换为 Tablesaw Table
        Table t = Table.create("y = x^2")
                .addColumns(
                        DoubleColumn.create("x", x),
                        DoubleColumn.create("y", y)
                );

        // 绘制折线图
        Plot.show(LinePlot.create("y = x^2", t, "x", "y"));
    }
}
