package org.matsim.project.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

public class BresenhamLine {

    static class Point {
        double x, y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        double distance(Point p) {
            return Math.sqrt((x - p.x) * (x - p.x) + (y - p.y) * (y - p.y));
        }
    }

    public static int floorDiv(int dividend, int divisor) {
        int result = dividend / divisor;
        if (dividend < 0 && dividend % divisor != 0) {
            result--;
        }
        return result;
    }

    public static Set<Pair> bresenhamLine(int x0, int y0, int x1, int y1, int tile_size, int factor) {
        Set<Pair> tiles = new HashSet<>(); // Using HashSet to automatically handle duplicates

        // Multiply coordinates by factor to enhance precision
        x0 *= factor;
        y0 *= factor;
        x1 *= factor;
        y1 *= factor;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = (x0 < x1) ? 1 : -1;
        int sy = (y0 < y1) ? 1 : -1;
        int err = dx - dy;

        while (true) {
            tiles.add(new Pair(floorDiv(x0, tile_size * factor), floorDiv(y0, tile_size * factor)));

            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err = err - dy;
                x0 = x0 + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                y0 = y0 + sy;
            }
        }
        return tiles; // Return the HashSet
    }

    // You can use this as a default call with tile_size=100 and factor=10
    public static Set<Pair> bresenhamLine(int x0, int y0, int x1, int y1) {
        return bresenhamLine(x0, y0, x1, y1, 100, 10);
    }

    // Pair class to store the coordinate pairs
    public static class Pair {
        public int x, y;

        public Pair(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Pair pair = (Pair) o;
            return x == pair.x && y == pair.y;
        }

        @Override
        public int hashCode() {
            // int result = x;
            // result = 31 * result + y;
            // return result;
            return Objects.hash(x, y);
        }
    }

    public static double segmentInsideSquare(double m, double c, double a, double b, double L, int x0, int y0, int x1,
            int y1) {
        Point[] intersections = new Point[4];

        // With bottom side
        intersections[0] = new Point((b - c) / m, b);
        // With top side
        intersections[1] = new Point((b + L - c) / m, b + L);
        // With left side
        intersections[2] = new Point(a, m * a + c);
        // With right side
        intersections[3] = new Point(a + L, m * (a + L) + c);

        Point p1 = null, p2 = null;

        for (Point p : intersections) {
            if (p.x >= a && p.x <= a + L && p.y >= b && p.y <= b + L) {
                if (p1 == null) {
                    p1 = p;
                } else {
                    p2 = p;
                }
            }
        }

        if (p1 != null && p2 != null) {
            // // ensure points are between x0,y0 and x1,y1, if not set to the closest point
            int xmax = Math.max(x0, x1), xmin = Math.min(x0, x1), ymax = Math.max(y0, y1), ymin = Math.min(y0, y1);
            if (p1.x < xmin) {
                p1.x = xmin;
                p1.y = m * xmin + c;
            } else if (p1.x > xmax) {
                p1.x = xmax;
                p1.y = m * xmax + c;
            }
            if (p1.y < ymin) {
                p1.y = ymin;
                p1.x = (ymin - c) / m;
            } else if (p1.y > ymax) {
                p1.y = ymax;
                p1.x = (ymax - c) / m;
            }
            if (p2.x < xmin) {
                p2.x = xmin;
                p2.y = m * xmin + c;
            } else if (p2.x > xmax) {
                p2.x = xmax;
                p2.y = m * xmax + c;
            }
            if (p2.y < ymin) {
                p2.y = ymin;
                p2.x = (ymin - c) / m;
            } else if (p2.y > ymax) {
                p2.y = ymax;
                p2.x = (ymax - c) / m;
            }
            return p1.distance(p2);
        }

        return 0.0;
    }

    public static void main(String[] args) {
        int x0 = 58, y0 = 18, x1 = -278, y1 = -635;
        // Example usage:
        Set<Pair> tiles = bresenhamLine(x0, y0, x1, y1, 100, 10);

        // calc slope and intercept
        double m = ((double) y1 - y0) / ((double) x1 - x0); // slope (y2-y1)/(x2-x1
        double c = y0 - m * x0; // intercept
        double line_length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        double segment_sum = 0;
        for (Pair p : tiles) {
            double dist = segmentInsideSquare(m, c, p.x * 100, p.y * 100, 100, x0, y0, x1, y1);
            segment_sum += dist;
            System.out.println("Distance: " + dist);
        }
        System.out.println("Total distance: " + segment_sum);
        System.out.println("Line length: " + line_length);
    }
}
