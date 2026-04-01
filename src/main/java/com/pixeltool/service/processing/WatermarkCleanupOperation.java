package com.pixeltool.service.processing;

import com.pixeltool.dto.ProcessOptions;
import com.pixeltool.util.ImageSupport;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class WatermarkCleanupOperation implements ImageOperation {

    @Override
    public boolean supports(ProcessingContext context) {
        return context.getOptions().isRemoveWatermark();
    }

    @Override
    public void apply(ProcessingContext context) {
        BufferedImage source = context.getCurrentImage();
        BufferedImage target = ImageSupport.copy(source);
        Color background = context.getEstimatedBackground();
        if (background == null) {
            background = ImageSupport.estimateBackground(source);
            context.setEstimatedBackground(background);
        }

        Rectangle region = selectRegion(source, context.getOptions(), background);
        if (region == null) {
            return;
        }

        List<int[]> candidates = new ArrayList<int[]>();
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                Color color = new Color(source.getRGB(x, y), true);
                if (isWatermarkPixel(color, background, context.getOptions())) {
                    candidates.add(new int[]{x, y});
                }
            }
        }

        if (candidates.size() < Math.max(20, region.width * region.height / 400)) {
            return;
        }

        for (int[] point : candidates) {
            int x = point[0];
            int y = point[1];
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    int actualX = x + offsetX;
                    int actualY = y + offsetY;
                    if (actualX < 0 || actualX >= target.getWidth() || actualY < 0 || actualY >= target.getHeight()) {
                        continue;
                    }
                    target.setRGB(actualX, actualY, background.getRGB());
                }
            }
        }
        context.setCurrentImage(target);
    }

    private Rectangle selectRegion(BufferedImage image, ProcessOptions options, Color background) {
        List<Rectangle> regions = buildRegions(image, options);
        String corner = options.getWatermarkCorner() == null ? "AUTO" : options.getWatermarkCorner().toUpperCase(Locale.ROOT);
        if (!"AUTO".equals(corner)) {
            for (Rectangle region : regions) {
                if (corner.equals(regionName(image, region))) {
                    return region;
                }
            }
            return null;
        }
        Rectangle bestRegion = null;
        int bestScore = 0;
        for (Rectangle region : regions) {
            int score = estimateCandidateScore(image, region, background, options);
            if (score > bestScore) {
                bestScore = score;
                bestRegion = region;
            }
        }
        return bestRegion;
    }

    private List<Rectangle> buildRegions(BufferedImage image, ProcessOptions options) {
        int width = Math.max(60, image.getWidth() / 4);
        int height = Math.max(60, image.getHeight() / 4);
        List<Rectangle> rectangles = new ArrayList<Rectangle>();
        rectangles.add(new Rectangle(0, 0, width, height));
        rectangles.add(new Rectangle(image.getWidth() - width, 0, width, height));
        rectangles.add(new Rectangle(0, image.getHeight() - height, width, height));
        rectangles.add(new Rectangle(image.getWidth() - width, image.getHeight() - height, width, height));
        return rectangles;
    }

    private String regionName(BufferedImage image, Rectangle region) {
        boolean left = region.x == 0;
        boolean top = region.y == 0;
        if (left && top) {
            return "TOP_LEFT";
        }
        if (!left && top) {
            return "TOP_RIGHT";
        }
        if (left) {
            return "BOTTOM_LEFT";
        }
        return "BOTTOM_RIGHT";
    }

    private int estimateCandidateScore(BufferedImage image, Rectangle region, Color background, ProcessOptions options) {
        int score = 0;
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (isWatermarkPixel(color, background, options)) {
                    score++;
                }
            }
        }
        return score;
    }

    private boolean isWatermarkPixel(Color color, Color background, ProcessOptions options) {
        int distance = ImageSupport.colorDistance(color, background);
        int brightnessGap = Math.abs(ImageSupport.brightness(color) - ImageSupport.brightness(background));
        return color.getAlpha() > options.getAlphaThreshold()
                && distance <= options.getWatermarkTolerance()
                && brightnessGap >= 4
                && brightnessGap <= options.getWatermarkTolerance()
                && ImageSupport.saturation(color) <= 50;
    }
}
