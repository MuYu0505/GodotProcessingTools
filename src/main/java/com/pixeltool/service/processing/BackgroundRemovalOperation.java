package com.pixeltool.service.processing;

import com.pixeltool.util.ImageSupport;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;

@Component
public class BackgroundRemovalOperation implements ImageOperation {

    @Override
    public boolean supports(ProcessingContext context) {
        if (context.getOptions().isRemoveBackground() || context.getOptions().isExtractMask()) {
            return true;
        }
        if (context.getEditOptions() == null) {
            return false;
        }
        if (context.getEditOptions().getSamplePoints() != null && !context.getEditOptions().getSamplePoints().isEmpty()) {
            return true;
        }
        return context.getEditOptions().getSampleX() != null && context.getEditOptions().getSampleY() != null;
    }

    @Override
    public void apply(ProcessingContext context) {
        BufferedImage source = context.getCurrentImage();
        Color background = context.getEstimatedBackground();
        java.util.List<java.awt.Point> samplePoints = new java.util.ArrayList<>();
        if (context.getEditOptions() != null && context.getEditOptions().getSamplePoints() != null) {
            for (java.util.Map<String, Object> p : context.getEditOptions().getSamplePoints()) {
                if (p.get("x") != null && p.get("y") != null) {
                    samplePoints.add(new java.awt.Point(((Number) p.get("x")).intValue(), ((Number) p.get("y")).intValue()));
                }
            }
        } else {
            Integer sampleX = context.getEditOptions() == null ? null : context.getEditOptions().getSampleX();
            Integer sampleY = context.getEditOptions() == null ? null : context.getEditOptions().getSampleY();
            if (sampleX != null && sampleY != null) {
                samplePoints.add(new java.awt.Point(sampleX, sampleY));
            }
        }

        if (!samplePoints.isEmpty()) {
            java.awt.Point firstPoint = samplePoints.get(0);
            if (firstPoint.x >= 0 && firstPoint.x < source.getWidth() && firstPoint.y >= 0 && firstPoint.y < source.getHeight()) {
                background = new Color(source.getRGB(firstPoint.x, firstPoint.y), true);
                context.setEstimatedBackground(background);
            }
        } else if (background == null) {
            background = ImageSupport.estimateBackground(source);
            context.setEstimatedBackground(background);
        }

        boolean[][] backgroundPixels = ImageSupport.detectBackground(
                source,
                samplePoints,
                background,
                context.getOptions().getBackgroundTolerance(),
                context.getOptions().getAlphaThreshold()
        );

        BufferedImage transparent = ImageSupport.transparentCanvas(source.getWidth(), source.getHeight());
        BufferedImage mask = ImageSupport.transparentCanvas(source.getWidth(), source.getHeight());

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if (backgroundPixels[y][x]) {
                    transparent.setRGB(x, y, 0x00000000);
                    mask.setRGB(x, y, new Color(0, 0, 0, 255).getRGB());
                } else {
                    int argb = source.getRGB(x, y);
                    transparent.setRGB(x, y, argb);
                    mask.setRGB(x, y, new Color(255, 255, 255, 255).getRGB());
                }
            }
        }

        // 消除边缘白边 (Defringe)
        defringe(transparent, backgroundPixels, background);

        boolean hasManualSeed = !samplePoints.isEmpty();
        if (context.getOptions().isRemoveBackground() || hasManualSeed) {
            context.setTransparentImage(transparent);
            context.setCurrentImage(transparent);
        }
        if (context.getOptions().isExtractMask()) {
            context.setMaskImage(mask);
        }
    }

    private void defringe(BufferedImage image, boolean[][] backgroundPixels, Color bgColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 查找边界像素
        boolean[][] edgePixels = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!backgroundPixels[y][x]) {
                    // 如果相邻有背景像素，则认为是边缘
                    boolean isEdge = false;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                if (backgroundPixels[ny][nx]) {
                                    isEdge = true;
                                    break;
                                }
                            }
                        }
                        if (isEdge) break;
                    }
                    edgePixels[y][x] = isEdge;
                }
            }
        }

        // 处理边缘像素
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (edgePixels[y][x]) {
                    Color c = new Color(image.getRGB(x, y), true);
                    if (c.getAlpha() == 0) continue;
                    
                    // 寻找周围非边缘的内部像素的颜色，作为真实颜色的参考
                    int r = c.getRed();
                    int g = c.getGreen();
                    int b = c.getBlue();
                    
                    int innerR = r, innerG = g, innerB = b;
                    int innerCount = 0;
                    
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                if (!backgroundPixels[ny][nx] && !edgePixels[ny][nx]) {
                                    Color ic = new Color(image.getRGB(nx, ny), true);
                                    innerR += ic.getRed();
                                    innerG += ic.getGreen();
                                    innerB += ic.getBlue();
                                    innerCount++;
                                }
                            }
                        }
                    }
                    
                    if (innerCount > 0) {
                        innerR /= (innerCount + 1);
                        innerG /= (innerCount + 1);
                        innerB /= (innerCount + 1);
                    }
                    
                    // 根据当前颜色与背景色的相似度，计算 alpha (0~255)
                    int distToBg = ImageSupport.colorDistance(c, bgColor);
                    int distToInner = ImageSupport.colorDistance(c, new Color(innerR, innerG, innerB));
                    
                    // 如果离背景色更近，则更透明；离内部色更近，则更不透明
                    float alphaRatio = 1.0f;
                    if (distToBg + distToInner > 0) {
                        alphaRatio = (float) distToBg / (distToBg + distToInner);
                    }
                    // 增加对比度，让边缘更清晰
                    alphaRatio = (float) Math.pow(alphaRatio, 1.5);
                    
                    int newAlpha = (int) (255 * alphaRatio);
                    newAlpha = Math.max(0, Math.min(255, newAlpha));
                    
                    // 使用内部颜色和计算出的alpha重新赋值，以去除白边颜色污染
                    image.setRGB(x, y, new Color(innerR, innerG, innerB, newAlpha).getRGB());
                }
            }
        }
    }
}
