package cn.bitloom.node;

import javafx.scene.control.TextArea;
import javafx.scene.text.Text;

/**
 * 自动调整高度的文本输入区域，继承 {@link TextArea}。
 *
 * <p>重写 {@link #computePrefHeight(double)}，根据内容实际渲染高度（包含自动换行）
 * 计算首选高度，让布局系统自然获取正确高度，避免手动 {@code setPrefHeight} 与布局系统冲突。
 *
 * <p>高度范围：48px（1 行）~ 126px（5 行），超过 5 行后固定高度，
 * TextArea 内部自动显示滚动条。
 *
 * <p>常量说明：
 * <ul>
 *   <li>{@link #LINE_HEIGHT} = 22：单行高度（含行间距）</li>
 *   <li>{@link #VERTICAL_PADDING} = 16：上下内边距总和</li>
 *   <li>{@link #MIN_HEIGHT} = 48：最小高度（1 行）</li>
 *   <li>{@link #MAX_HEIGHT} = 126：最大高度（5 行）</li>
 *   <li>{@link #CONTENT_WIDTH_OFFSET} = 76：内容宽度偏移（padding 60+16 + 滚动条预留）</li>
 * </ul>
 *
 * <p>设计原理：
 * <ul>
 *   <li>JavaFX 布局系统通过 {@code computePrefHeight()} 获取组件首选高度</li>
 *   <li>重写此方法让布局系统自然获取正确高度，避免手动 {@code setPrefHeight()} 与布局系统冲突</li>
 *   <li>解决了手动设值导致的闪烁和失焦还原问题</li>
 *   <li>使用 {@link Text} 节点测量内容实际高度（包含自动换行），考虑实际可用宽度</li>
 * </ul>
 */
public class AutoResizeTextArea extends TextArea {

    private static final double LINE_HEIGHT = 22;
    private static final double VERTICAL_PADDING = 16;
    private static final double MIN_HEIGHT = 48;
    private static final double MAX_HEIGHT = 126;
    private static final double CONTENT_WIDTH_OFFSET = 76;

    public AutoResizeTextArea() {
        super();
        setWrapText(true);
        // 内容变化时触发重新布局，computePrefHeight 会被布局系统重新调用
        textProperty().addListener((obs, oldV, newV) -> requestLayout());
    }

    @Override
    protected double computePrefHeight(double width) {
        if (width <= 0) {
            return MIN_HEIGHT;
        }
        double contentWidth = Math.max(1, width - CONTENT_WIDTH_OFFSET);
        Text helper = new Text(getText());
        helper.setFont(getFont());
        helper.setWrappingWidth(contentWidth);
        double textHeight = helper.getLayoutBounds().getHeight();
        double totalHeight = textHeight + VERTICAL_PADDING;
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, totalHeight));
    }
}
