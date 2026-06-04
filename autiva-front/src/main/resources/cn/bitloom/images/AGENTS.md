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
- 打包时转换为多分辨率 ICO（16/24/32/48/64/128/256）

**尺寸：** 建议使用 256x256 或更大

**ICO 生成：** 打包脚本（build-package.ps1）使用 HighQualityBicubic 缩放从 PNG 生成多分辨率 ICO 文件，每个尺寸以 PNG 格式嵌入，确保各场景下图标清晰

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

#### octopus.svg
章鱼桌面宠物图标（SVG 格式）。

**用途：**
- 最小化到托盘时的桌面宠物
- 始终置顶的可交互图标

**特点：**
- 彩虹渐变配色（与 icon-color.svg 风格一致）
- 可爱的卡通章鱼造型
- 包含眼睛、微笑、腮红细节

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

#### stop.svg
停止图标（方形）。

**用途：**
- 暂停流式生成按钮（点击后暂停生成并保留部分响应）

#### file.svg
文件图标（白色描边，含代码符号）。

**用途：**
- 文件编辑器中的文件图标（文件树、Tab标签）

#### folder.svg
文件夹图标（白色描边，灰色填充）。

**用途：**
- 文件编辑器中的文件夹图标（文件树）

#### file-new.svg
新建文件图标（文件+加号）。

**用途：**
- 文件编辑器工具栏"新建文件"按钮

#### folder-new.svg
新建文件夹图标（文件夹+加号）。

**用途：**
- 文件编辑器工具栏"新建文件夹"按钮

#### file-tree.svg
文件树图标（文件夹+横线）。

**用途：**
- 文件编辑器左侧栏"文件树切换"按钮

#### preview.svg
预览图标（文档+画笔）。

**用途：**
- 文件编辑器右侧栏"Markdown预览"按钮

#### format.svg
格式化图标（文本行+右箭头）。

**用途：**
- 文件编辑器右侧栏"代码格式化"按钮

#### canvas.svg
画布图标（矩形+山脉+太阳）。

**用途：**
- 侧边栏画布导航项图标

### 画布工具图标

所有画布工具和属性面板图标以 `canvas-` 前缀命名。

**统一规范：** viewBox `0 0 24 24`，stroke `#1d1d1f`，stroke-width `1.5`，stroke-linecap `round`，stroke-linejoin `round`，fill `none`。

#### 工具栏图标
| 文件 | 说明 |
|------|------|
| canvas-select.svg | 选择工具（鼠标指针箭头） |
| canvas-rectangle.svg | 矩形工具（圆角矩形） |
| canvas-diamond.svg | 菱形工具（旋转正方形） |
| canvas-ellipse.svg | 椭圆工具（椭圆，非正圆） |
| canvas-arrow.svg | 箭头工具（斜线+箭头） |
| canvas-line.svg | 线条工具（对角线） |
| canvas-freehand.svg | 手绘工具（波浪曲线） |
| canvas-text.svg | 文字工具（T 字形） |
| canvas-send.svg | 发送按钮（纸飞机） |

#### 属性面板图标
| 文件 | 说明 |
|------|------|
| canvas-stroke-thin.svg | 细线宽 |
| canvas-stroke-medium.svg | 中线宽 |
| canvas-stroke-thick.svg | 粗线宽 |
| canvas-line-solid.svg | 实线样式 |
| canvas-line-dashed.svg | 虚线样式 |
| canvas-line-dotted.svg | 点线样式 |
| canvas-rough-neat.svg | 板正手绘风格 |
| canvas-rough-rough.svg | 潦草手绘风格 |
| canvas-rough-messy.svg | 很潦草手绘风格 |
| canvas-corner-sharp.svg | 直角边角 |
| canvas-corner-round.svg | 圆角边角 |

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
