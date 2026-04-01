package com.pixeltool.util;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Queue;

public final class ImageSupport {

    private ImageSupport() {
    }

    public static BufferedImage read(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("无法读取图片文件: " + file.getAbsolutePath());
        }
        return toArgb(image);
    }

    public static void writePng(BufferedImage image, File file) throws IOException {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", file);
    }

    public static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return target;
    }

    public static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return target;
    }

    public static BufferedImage decodeDataUrl(String dataUrl) throws IOException {
        if (dataUrl == null || dataUrl.trim().isEmpty()) {
            return null;
        }
        int commaIndex = dataUrl.indexOf(',');
        String base64Content = commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
        byte[] bytes = Base64.getDecoder().decode(base64Content);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("无法解析前端绘制的蒙版数据");
        }
        return toArgb(image);
    }

    public static String toDataUrl(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static Color estimateBackground(BufferedImage image) {
        int block = Math.max(8, Math.min(image.getWidth(), image.getHeight()) / 24);
        long red = 0;
        long green = 0;
        long blue = 0;
        long count = 0;
        int[] xs = new int[]{0, image.getWidth() - block};
        int[] ys = new int[]{0, image.getHeight() - block};
        for (int startX : xs) {
            for (int startY : ys) {
                for (int y = startY; y < startY + block && y < image.getHeight(); y++) {
                    for (int x = startX; x < startX + block && x < image.getWidth(); x++) {
                        Color color = new Color(image.getRGB(x, y), true);
                        red += color.getRed();
                        green += color.getGreen();
                        blue += color.getBlue();
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            return new Color(255, 255, 255);
        }
        return new Color((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    public static boolean[][] detectBackground(BufferedImage image, List<Point> seedPoints, Color fallbackBackground, int tolerance, int alphaThreshold, int edgeBoost) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] visited = new boolean[height][width];
        
        // Use a list of target colors
        List<Color> targetColors = new ArrayList<>();
        targetColors.add(fallbackBackground);
        if (seedPoints != null) {
            for (Point p : seedPoints) {
                if (p.x >= 0 && p.x < width && p.y >= 0 && p.y < height) {
                    targetColors.add(new Color(image.getRGB(p.x, p.y), true));
                }
            }
        }

        Queue<Point> queue = new ArrayDeque<Point>();
        for (int x = 0; x < width; x++) {
            enqueueBorderMulti(image, targetColors, tolerance, alphaThreshold, visited, queue, x, 0);
            enqueueBorderMulti(image, targetColors, tolerance, alphaThreshold, visited, queue, x, height - 1);
        }
        for (int y = 0; y < height; y++) {
            enqueueBorderMulti(image, targetColors, tolerance, alphaThreshold, visited, queue, 0, y);
            enqueueBorderMulti(image, targetColors, tolerance, alphaThreshold, visited, queue, width - 1, y);
        }

        // Add user-selected seed points directly to the queue
        if (seedPoints != null) {
            for (Point p : seedPoints) {
                if (p.x >= 0 && p.x < width && p.y >= 0 && p.y < height) {
                    if (!visited[p.y][p.x]) {
                        visited[p.y][p.x] = true;
                        queue.add(new Point(p.x, p.y));
                    }
                }
            }
        }

        int[] dx = new int[]{1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = new int[]{0, 0, 1, -1, 1, -1, 1, -1};
        while (!queue.isEmpty()) {
            Point point = queue.poll();
            for (int i = 0; i < dx.length; i++) {
                int nextX = point.x + dx[i];
                int nextY = point.y + dy[i];
                if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height || visited[nextY][nextX]) {
                    continue;
                }
                if (matchesAnyBackground(image, nextX, nextY, targetColors, tolerance, alphaThreshold)) {
                    visited[nextY][nextX] = true;
                    queue.add(new Point(nextX, nextY));
                }
            }
        }

        boolean[][] refined = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(visited[y], 0, refined[y], 0, width);
        }

        int edgeTolerance = tolerance + Math.max(0, edgeBoost);

        for (int pass = 0; pass < 2; pass++) {
            boolean[][] next = new boolean[height][width];
            for (int y = 0; y < height; y++) {
                System.arraycopy(refined[y], 0, next[y], 0, width);
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (next[y][x]) {
                        continue;
                    }
                    if (!matchesAnyBackground(image, x, y, targetColors, edgeTolerance, alphaThreshold)) {
                        continue;
                    }
                    int bgNeighbors = 0;
                    for (int i = 0; i < dx.length; i++) {
                        int nx = x + dx[i];
                        int ny = y + dy[i];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                            continue;
                        }
                        if (refined[ny][nx]) {
                            bgNeighbors++;
                        }
                    }
                    if (bgNeighbors >= 4) {
                        next[y][x] = true;
                    }
                }
            }
            refined = next;
        }

        if (edgeBoost > 0) {
            for (int pass = 0; pass < 2; pass++) {
                boolean[][] next = new boolean[height][width];
                for (int y = 0; y < height; y++) {
                    System.arraycopy(refined[y], 0, next[y], 0, width);
                }
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (next[y][x]) {
                            continue;
                        }
                        if (!matchesAnyBackground(image, x, y, targetColors, edgeTolerance, alphaThreshold)) {
                            continue;
                        }
                        boolean nearBackground = false;
                        for (int i = 0; i < dx.length; i++) {
                            int nx = x + dx[i];
                            int ny = y + dy[i];
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                                continue;
                            }
                            if (refined[ny][nx]) {
                                nearBackground = true;
                                break;
                            }
                        }
                        if (nearBackground) {
                            next[y][x] = true;
                        }
                    }
                }
                refined = next;
            }
        }
        return refined;
    }

    private static void enqueueBorderMulti(BufferedImage image, List<Color> targetColors, int tolerance, int alphaThreshold,
                                      boolean[][] visited, Queue<Point> queue, int x, int y) {
        if (visited[y][x]) {
            return;
        }
        if (matchesAnyBackground(image, x, y, targetColors, tolerance, alphaThreshold)) {
            visited[y][x] = true;
            queue.add(new Point(x, y));
        }
    }

    private static boolean matchesAnyBackground(BufferedImage image, int x, int y, List<Color> targetColors, int tolerance, int alphaThreshold) {
        Color color = new Color(image.getRGB(x, y), true);
        if (color.getAlpha() <= alphaThreshold) {
            return true;
        }
        for (Color bg : targetColors) {
            if (colorDistance(color, bg) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    private static void enqueueBorder(BufferedImage image, Color background, int tolerance, int alphaThreshold,
                                      boolean[][] visited, Queue<Point> queue, int x, int y) {
        if (visited[y][x]) {
            return;
        }
        if (matchesBackground(image, x, y, background, tolerance, alphaThreshold)) {
            visited[y][x] = true;
            queue.add(new Point(x, y));
        }
    }

    private static boolean matchesBackground(BufferedImage image, int x, int y, Color background, int tolerance, int alphaThreshold) {
        Color color = new Color(image.getRGB(x, y), true);
        if (color.getAlpha() <= alphaThreshold) {
            return true;
        }
        return colorDistance(color, background) <= tolerance;
    }

    public static int colorDistance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return (int) Math.sqrt(red * red + green * green + blue * blue);
    }

    public static int saturation(Color color) {
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        return max - min;
    }

    public static int brightness(Color color) {
        return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
    }

    public static Rectangle findOpaqueBounds(BufferedImage image, int alphaThreshold) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > alphaThreshold) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public static BufferedImage crop(BufferedImage image, Rectangle bounds) {
        BufferedImage target = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(image, 0, 0, bounds.width, bounds.height, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, null);
        graphics.dispose();
        return target;
    }

    public static Rectangle clampRectangle(BufferedImage image, Integer x, Integer y, Integer width, Integer height) {
        int actualX = Math.max(0, x == null ? 0 : x);
        int actualY = Math.max(0, y == null ? 0 : y);
        int requestedWidth = width == null ? image.getWidth() : width;
        int requestedHeight = height == null ? image.getHeight() : height;
        int actualWidth = Math.max(1, Math.min(requestedWidth, image.getWidth() - actualX));
        int actualHeight = Math.max(1, Math.min(requestedHeight, image.getHeight() - actualY));
        return new Rectangle(actualX, actualY, actualWidth, actualHeight);
    }

    public static BufferedImage resizeNearest(BufferedImage image, int width, int height) {
        Image scaledImage = image.getScaledInstance(width, height, Image.SCALE_REPLICATE);
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        graphics.drawImage(scaledImage, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    public static BufferedImage transparentCanvas(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public static BufferedImage solidCanvas(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }
}
