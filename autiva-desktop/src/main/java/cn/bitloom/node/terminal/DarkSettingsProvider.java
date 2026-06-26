package cn.bitloom.node.terminal;

import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.scene.text.Font;
import org.jetbrains.annotations.NotNull;

/**
 * 深色主题的终端设置 - PowerShell 风格
 * 背景 #0c0c0c，前景 #cccccc
 */
public class DarkSettingsProvider extends DefaultSettingsProvider {

    // PowerShell 深黑背景
    private static final TerminalColor DARK_BACKGROUND = TerminalColor.rgb(12, 12, 12); // #0c0c0c

    // PowerShell 浅灰前景（文字）
    private static final TerminalColor LIGHT_FOREGROUND = TerminalColor.rgb(204, 204, 204); // #cccccc

    // 自定义深色主题调色板 - 将 ANSI 黑色改为浅灰色（用于光标）
    private static final ColorPalette DARK_PALETTE = new ColorPalette() {
        // 基于 XTERM 调色板，但将索引 0（黑色）改为浅灰色
        private final Color[] colors = new Color[]{
                new Color(0xcccccc), // 索引 0: 浅灰色（用于光标，原来是黑色）
                new Color(0xcd0000), // 索引 1: 红色
                new Color(0x00cd00), // 索引 2: 绿色
                new Color(0xcdcd00), // 索引 3: 黄色
                new Color(0x1e90ff), // 索引 4: 蓝色
                new Color(0xcd00cd), // 索引 5: 紫色
                new Color(0x00cdcd), // 索引 6: 青色
                new Color(0xe5e5e5), // 索引 7: 白色
                // Bright versions
                new Color(0x4c4c4c), // 索引 8: 深灰色（原来是亮黑色）
                new Color(0xff0000), // 索引 9: 亮红色
                new Color(0x00ff00), // 索引 10: 亮绿色
                new Color(0xffff00), // 索引 11: 亮黄色
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
        return DARK_BACKGROUND;
    }

    @Override
    public @NotNull TerminalColor getDefaultForeground() {
        return LIGHT_FOREGROUND;
    }

    @Override
    public ColorPalette getTerminalColorPalette() {
        // 使用自定义深色调色板，光标颜色为浅灰色
        return DARK_PALETTE;
    }

    @Override
    public Font getTerminalFont() {
        // Windows: Consolas, macOS: Menlo, Linux: Monospaced
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
        // 选中时：白色背景 + 黑色文字（高对比度，清晰可见）
        return new TextStyle(TerminalColor.rgb(0, 0, 0), TerminalColor.rgb(255, 255, 255));
    }

    @Override
    public boolean useInverseSelectionColor() {
        // 不使用反转选择颜色，直接使用上面定义的颜色
        return false;
    }

    @Override
    public @NotNull TextStyle getFoundPatternColor() {
        // 搜索匹配：黄色背景 + 黑色文字
        return new TextStyle(TerminalColor.rgb(0, 0, 0), TerminalColor.rgb(255, 255, 0));
    }
}