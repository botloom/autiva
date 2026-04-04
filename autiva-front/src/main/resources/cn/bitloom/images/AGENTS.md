# Images 目录

## 概述
本目录包含应用使用的图像资源文件。

## 图像列表

### 应用图标

#### icon.png
应用主图标（PNG 格式）。

**用途：**
- 窗口图标
- 任务栏图标

**尺寸：** 建议使用 256x256 或更大

#### icon.svg
应用主图标（SVG 格式）。

**用途：**
- 矢量图标
- 可缩放显示

#### icon-color.svg
彩色应用图标（SVG 格式）。

**用途：**
- 主页显示的应用图标
- 启动画面

### UI 图标

#### arrow-up.svg
向上箭头图标。

**用途：**
- 发送按钮

**颜色：** 可通过 CSS 修改

#### left.svg
向左箭头图标。

**用途：**
- 浏览器后退按钮

#### right.svg
向右箭头图标。

**用途：**
- 浏览器前进按钮

#### reload.svg
刷新图标。

**用途：**
- 浏览器刷新按钮

#### plus.svg
加号图标。

**用途：**
- 添加按钮

#### voice.svg
麦克风图标。

**用途：**
- 语音输入按钮（预留）

## 使用方式

### 在 FXML 中使用 SVG
```xml
<?import cn.bitloom.node.SvgImageView?>

<SvgImageView fitWidth="20" fitHeight="20" 
              svgPath="/cn/bitloom/images/arrow-up.svg"/>
```

### 在 FXML 中使用 PNG
```xml
<?import javafx.scene.image.ImageView?>
<?import javafx.scene.image.Image?>

<ImageView fitWidth="20" fitHeight="20">
    <image>
        <Image url="@../images/icon.png"/>
    </image>
</ImageView>
```

### 设置窗口图标
```java
stage.getIcons().add(new Image(getClass().getResourceAsStream("/cn/bitloom/images/icon.png")));
```

## 图标规范

### SVG 图标优势
- 矢量格式，可无限缩放
- 文件体积小
- 可通过 CSS 修改颜色

### 尺寸建议
- 工具栏图标：20x20
- 按钮图标：20x20
- 应用图标：100x100（主页显示）

### 颜色规范
- 图标默认使用单色
- 可通过 SvgImageView 的样式修改颜色
- 保持与应用主题一致

## 注意事项
1. SVG 图标使用 SvgImageView 组件加载
2. PNG 图标使用标准 ImageView 加载
3. 路径使用类路径资源路径
4. SVG 图标会自动转换为 PNG 显示
