# Node 包

## 概述
本包定义了自定义 JavaFX 节点组件。

## 核心类

### SvgImageView
自定义 ImageView，支持 SVG 图像加载和渲染。

**功能：**
- 加载 SVG 资源文件
- 自动转换为 PNG 格式显示
- 支持指定尺寸

**字段：**
- `svgPath`: SVG 文件路径

**方法：**
- `setSvgPath(String)`: 设置 SVG 路径并加载
- `loadSvg()`: 内部方法，加载并转换 SVG

## 使用示例

### FXML 中使用
```xml
<SvgImageView fx:id="icon" svgPath="/cn/bitloom/images/icon.svg" 
              fitWidth="32" fitHeight="32"/>
```

### 代码中使用
```java
SvgImageView imageView = new SvgImageView();
imageView.setFitWidth(32);
imageView.setFitHeight(32);
imageView.setSvgPath("/cn/bitloom/images/icon.svg");
```

## 实现原理

### SVG 转换流程
1. 读取 SVG 文件内容
2. 使用 Apache Batik 的 PNGTranscoder 转换
3. 设置目标宽高
4. 转换为 BufferedImage
5. 使用 SwingFXUtils 转换为 JavaFX Image

### 依赖库
- Apache Batik: SVG 解析和转换
- JavaFX: 图像显示

## 错误处理
- 资源不存在：打印错误信息到 stderr
- 转换失败：打印异常信息到 stderr

## 设计模式
- 继承：扩展 ImageView 功能
- 封装：隐藏 SVG 转换细节

## 注意事项
1. SVG 路径是类路径资源路径
2. 尺寸通过 fitWidth/fitHeight 设置
3. 转换过程在对象创建时执行
4. 修改尺寸后需要重新设置 svgPath
