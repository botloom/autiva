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
- 延迟加载：先设置尺寸再加载 SVG，避免尺寸为0时渲染失败

**字段：**
- `svgPath`: SVG 文件路径
- `loaded`: 是否已加载完成

**方法：**
- `setSvgPath(String)`: 设置 SVG 路径并加载（如果尺寸已设置）
- `loadSvg()`: 内部方法，加载并转换 SVG

**加载机制：**
- 构造函数中注册 fitWidth/fitHeight 属性监听器
- 当尺寸从0变为正值且 svgPath 已设置时，自动触发加载
- `setSvgPath()` 在尺寸已设置时立即加载，否则等待尺寸设置后自动加载
- `loaded` 标志防止重复加载

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
3. **必须先设置尺寸（fitWidth/fitHeight），再设置 svgPath**，否则 SVG 无法正确渲染
4. 修改尺寸后需要重新设置 svgPath
5. loaded 标志确保 SVG 只加载一次
