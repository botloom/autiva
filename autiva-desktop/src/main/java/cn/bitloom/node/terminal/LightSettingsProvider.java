package cn.bitloom.node.terminal;

import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.scene.text.Font;
import org.jetbrains.annotations.NotNull;

/**
 * 浅色主题的终端设置 - 白底黑字
 * 背景 #ffffff，前景 #1d1d1f
 */
public class LightSettingsProvider extends DefaultSettingsProvider {

    // 白色背景
    private static final TerminalColor LIGHT_BACKGROUND = TerminalColor.rgb(255, 255, 255); // #ffffff

    // 深色前景（文字）
    private static final TerminalColor DARK_FOREGROUND = TerminalColor.rgb(29, 29, 31); // #1d1d1f

    // 自定义浅色调色板 - 将 ANSI 黑色改为深色（用于光标）
    private static final ColorPalette LIGHT_PALETTE = new ColorPalette() {
        private final Color[] colors = new Color[]{
                new Color(0x1d1d1f), // 索引 0: 深色（用于光标，原来是黑色）
                new Color(0xcd0000), // 索引 1: 红色
                new Color(0x2e7d32), // 索引 2: 绿色（白底可读）
                new Color(0x1d1d1f), // 索引 3: 黄色→黑色（PSReadLine 命令高亮）
                new Color(0x1e90ff), // 索引 4: 蓝色
                new Color(0xcd00cd), // 索引 5: 紫色
                new Color(0x0086a8), // 索引 6: 青色（白底可读）
                new Color(0xe5e5e5), // 索引 7: 白色
                // Bright versions
                new Color(0x4c4c4c), // 索引 8: 深灰色
                new Color(0xff0000), // 索引 9: 亮红色
                new Color(0x00ff00), // 索引 10: 亮绿色
                new Color(0x1d1d1f), // 索引 11: 亮黄色→黑色
                new Color(0x4682b4), // 索引 12: 亮蓝色
                new Color(0xff00ff), // 索引 13: 亮紫色
                new Color(0x00ffff), // 索引 14: 亮青色
                new Color(0xffffff), // 索引 15: 亮白色
        };

        @NotNull
        @Override
        public Color getForegroundByColorIndex(int colorIndex) {
            return colors[colorIndex];
        }

        @NotNull
        @Override
        protected Color getBackgroundByColorIndex(int colorIndex) {
            return colors[colorIndex];
        }
    };

    @Override
    public @NotNull TerminalColor getDefaultBackground() {
        return LIGHT_BACKGROUND;
    }

    @Override
    public @NotNull TerminalColor getDefaultForeground() {
        return DARK_FOREGROUND;
    }

    @Override
    public ColorPalette getTerminalColorPalette() {
        return LIGHT_PALETTE;
    }

    @Override
    public Font getTerminalFont() {
        String fontName;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            fontName = "Consolas";
        } else if (os.contains("mac")) {
            fontName = "Menlo";
        } else {
            fontName = "Monospaced";
        }
        return Font.font(fontName, getTerminalFontSize());
    }

    @Override
    public float getTerminalFontSize() {
        return 14;
    }

    @Override
    public @NotNull TextStyle getSelectionColor() {
        // 选中时：蓝色背景 + 白色文字
        return new TextStyle(TerminalColor.rgb(255, 255, 255), TerminalColor.rgb(0, 113, 227));
    }

    @Override
    public boolean useInverseSelectionColor() {
        return false;
    }

    @Override
    public @NotNull TextStyle getFoundPatternColor() {
        // 搜索匹配：黄色背景 + 黑色文字
        return new TextStyle(TerminalColor.rgb(0, 0, 0), TerminalColor.rgb(255, 255, 0));
    }
}
