package cn.bitloom.node.canvas.model;

import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 画布场景序列化/反序列化工具。
 * 将 CanvasScene 中的元素转为 JSON 或从 JSON 恢复。
 */
public class CanvasSceneSerializer {

    /**
     * 将元素列表序列化为 JSON 数组
     */
    public static ArrayNode toJsonArray(List<CanvasElement> elements) {
        ArrayNode array = JsonUtils.createArray();
        for (CanvasElement el : elements) {
            ObjectNode obj = elementToJson(el);
            array.add(obj);
        }
        return array;
    }

    /**
     * 从 JSON 数组反序列化元素列表
     */
    public static List<CanvasElement> fromJsonArray(ArrayNode array) {
        List<CanvasElement> elements = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode obj = array.get(i);
            CanvasElement el = jsonToElement(obj);
            if (el != null) {
                elements.add(el);
            }
        }
        return elements;
    }

    private static ObjectNode elementToJson(CanvasElement el) {
        ObjectNode obj = JsonUtils.createObject();
        obj.put("type", el.getType());
        obj.put("id", el.getId());
        obj.put("x", el.getX());
        obj.put("y", el.getY());
        obj.put("width", el.getWidth());
        obj.put("height", el.getHeight());
        obj.put("rotation", el.getRotation());
        obj.put("strokeColor", el.getStrokeColor());
        obj.put("fillColor", el.getFillColor());
        obj.put("strokeWidth", el.getStrokeWidth());
        obj.put("opacity", el.getOpacity());
        obj.put("roughness", el.getRoughness());
        obj.put("seed", el.getSeed());
        obj.put("lineStyle", el.getLineStyle());
        obj.put("arrowStyle", el.getArrowStyle());
        obj.put("visible", el.isVisible());
        obj.put("locked", el.isLocked());

        if (el instanceof TextElement textEl) {
            obj.put("text", textEl.getText());
            obj.put("fontSize", textEl.getFontSize());
        }
        if (el instanceof FreehandElement freehandEl) {
            ArrayNode points = JsonUtils.createArray();
            for (Point p : freehandEl.getPoints()) {
                ObjectNode pt = JsonUtils.createObject();
                pt.put("x", p.x());
                pt.put("y", p.y());
                points.add(pt);
            }
            obj.set("points", points);
        }

        return obj;
    }

    private static CanvasElement jsonToElement(JsonNode obj) {
        String type = getString(obj, "type");
        if (type == null) return null;

        CanvasElement el = switch (type) {
            case "rectangle" -> new RectangleElement();
            case "ellipse" -> new EllipseElement();
            case "diamond" -> new DiamondElement();
            case "line" -> new LineElement();
            case "arrow" -> new ArrowElement();
            case "text" -> new TextElement();
            case "freehand" -> new FreehandElement();
            default -> null;
        };

        if (el == null) return null;

        el.setX(obj.path("x").asDouble());
        el.setY(obj.path("y").asDouble());
        el.setWidth(obj.path("width").asDouble());
        el.setHeight(obj.path("height").asDouble());
        el.setRotation(obj.path("rotation").asDouble());
        el.setStrokeColor(getString(obj, "strokeColor"));
        el.setFillColor(getString(obj, "fillColor"));
        el.setStrokeWidth(obj.path("strokeWidth").asDouble());
        el.setOpacity(obj.path("opacity").asDouble());
        el.setRoughness(obj.path("roughness").asDouble());
        el.setSeed(obj.path("seed").asLong());
        el.setLineStyle(getString(obj, "lineStyle"));
        el.setArrowStyle(getString(obj, "arrowStyle"));
        el.setVisible(obj.path("visible").asBoolean(true));
        el.setLocked(obj.path("locked").asBoolean(false));

        if (el instanceof TextElement textEl) {
            textEl.setText(getString(obj, "text"));
            textEl.setFontSize(obj.path("fontSize").asDouble());
        }
        if (el instanceof FreehandElement freehandEl) {
            JsonNode points = obj.get("points");
            if (points != null && !points.isNull()) {
                for (int i = 0; i < points.size(); i++) {
                    JsonNode pt = points.get(i);
                    freehandEl.addPoint(new Point(pt.path("x").asDouble(), pt.path("y").asDouble()));
                }
            }
        }

        return el;
    }

    private static String getString(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
