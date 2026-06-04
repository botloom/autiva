package cn.bitloom.node.canvas.model;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

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
    public static JSONArray toJsonArray(List<CanvasElement> elements) {
        JSONArray array = new JSONArray();
        for (CanvasElement el : elements) {
            JSONObject obj = elementToJson(el);
            array.add(obj);
        }
        return array;
    }

    /**
     * 从 JSON 数组反序列化元素列表
     */
    public static List<CanvasElement> fromJsonArray(JSONArray array) {
        List<CanvasElement> elements = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            CanvasElement el = jsonToElement(obj);
            if (el != null) {
                elements.add(el);
            }
        }
        return elements;
    }

    private static JSONObject elementToJson(CanvasElement el) {
        JSONObject obj = new JSONObject();
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
            JSONArray points = new JSONArray();
            for (Point p : freehandEl.getPoints()) {
                JSONObject pt = new JSONObject();
                pt.put("x", p.x());
                pt.put("y", p.y());
                points.add(pt);
            }
            obj.put("points", points);
        }

        return obj;
    }

    private static CanvasElement jsonToElement(JSONObject obj) {
        String type = obj.getString("type");
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

        el.setX(obj.getDoubleValue("x"));
        el.setY(obj.getDoubleValue("y"));
        el.setWidth(obj.getDoubleValue("width"));
        el.setHeight(obj.getDoubleValue("height"));
        el.setRotation(obj.getDoubleValue("rotation"));
        el.setStrokeColor(obj.getString("strokeColor"));
        el.setFillColor(obj.getString("fillColor"));
        el.setStrokeWidth(obj.getDoubleValue("strokeWidth"));
        el.setOpacity(obj.getDoubleValue("opacity"));
        el.setRoughness(obj.getDoubleValue("roughness"));
        el.setSeed(obj.getLongValue("seed"));
        el.setLineStyle(obj.getString("lineStyle"));
        el.setArrowStyle(obj.getString("arrowStyle"));
        el.setVisible(obj.getBooleanValue("visible", true));
        el.setLocked(obj.getBooleanValue("locked", false));

        if (el instanceof TextElement textEl) {
            textEl.setText(obj.getString("text"));
            textEl.setFontSize(obj.getDoubleValue("fontSize"));
        }
        if (el instanceof FreehandElement freehandEl) {
            JSONArray points = obj.getJSONArray("points");
            if (points != null) {
                for (int i = 0; i < points.size(); i++) {
                    JSONObject pt = points.getJSONObject(i);
                    freehandEl.addPoint(new Point(pt.getDoubleValue("x"), pt.getDoubleValue("y")));
                }
            }
        }

        return el;
    }
}
