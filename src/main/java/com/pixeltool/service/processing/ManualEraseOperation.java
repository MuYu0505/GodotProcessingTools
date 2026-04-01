package com.pixeltool.service.processing;

import com.pixeltool.dto.ProcessOptions;
import com.pixeltool.util.ImageSupport;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;

@Component
public class ManualEraseOperation implements ImageOperation {

    @Override
    public boolean supports(ProcessingContext context) {
        return context.getEraseMask() != null;
    }

    @Override
    public void apply(ProcessingContext context) {
        BufferedImage mask = context.getEraseMask();
        BufferedImage source = context.getCurrentImage();
        BufferedImage target = ImageSupport.copy(source);

        int width = Math.min(mask.getWidth(), target.getWidth());
        int height = Math.min(mask.getHeight(), target.getHeight());
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (mask.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > 10) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return;
        }

        Color background = context.getEstimatedBackground();
        if (background == null) {
            background = ImageSupport.estimateBackground(source);
            context.setEstimatedBackground(background);
        }
        ProcessOptions options = context.getOptions();

        int margin = Math.max(6, Math.min(width, height) / 60);
        int startX = clamp(minX - margin, 0, width - 1);
        int endX = clamp(maxX + margin, 0, width - 1);
        int startY = clamp(minY - margin, 0, height - 1);
        int endY = clamp(maxY + margin, 0, height - 1);

        int backgroundRgb = background.getRGB();
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int maskAlpha = (mask.getRGB(x, y) >> 24) & 0xFF;
                if (maskAlpha > 10) {
                    fill3x3(target, backgroundRgb, x, y, width, height);
                    continue;
                }
                Color color = new Color(source.getRGB(x, y), true);
                if (ImageSupport.isWatermarkPixel(color, background, options)) {
                    fill3x3(target, backgroundRgb, x, y, width, height);
                }
            }
        }

        context.setCurrentImage(target);
    }

    private void fill3x3(BufferedImage image, int rgb, int x, int y, int width, int height) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ax = x + dx;
                int ay = y + dy;
                if (ax < 0 || ay < 0 || ax >= width || ay >= height) {
                    continue;
                }
                image.setRGB(ax, ay, rgb);
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
