package cn.bitloom.node.canvas.model;

public record Point(double x, double y) {
    public double distanceTo(Point other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Point offset(double dx, double dy) {
        return new Point(x + dx, y + dy);
    }
}
