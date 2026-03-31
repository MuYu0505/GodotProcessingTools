package com.pixeltool.service.processing;

import com.pixeltool.util.ImageSupport;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

@Component
public class PixelResizeOperation implements ImageOperation {

    @Override
    public boolean supports(ProcessingContext context) {
        return context.getOptions().isResizeEnabled();
    }

    @Override
    public void apply(ProcessingContext context) {
        BufferedImage source = context.getCurrentImage();
        context.setPreparedImage(source);
        Rectangle bounds = ImageSupport.findOpaqueBounds(source, context.getOptions().getAlphaThreshold());
        BufferedImage cropped = ImageSupport.crop(source, bounds);

        double scaleX = context.getOptions().getTargetWidth() * 1.0D / Math.max(1, cropped.getWidth());
        double scaleY = context.getOptions().getTargetHeight() * 1.0D / Math.max(1, cropped.getHeight());
        double scale = Math.min(scaleX, scaleY);

        int width = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(cropped.getHeight() * scale));

        BufferedImage resized = ImageSupport.resizeNearest(cropped, width, height);
        BufferedImage canvas = ImageSupport.transparentCanvas(
                context.getOptions().getTargetWidth(),
                context.getOptions().getTargetHeight()
        );

        Graphics2D graphics = canvas.createGraphics();
        int drawX = (canvas.getWidth() - resized.getWidth()) / 2;
        int drawY = (canvas.getHeight() - resized.getHeight()) / 2;
        graphics.drawImage(resized, drawX, drawY, null);
        graphics.dispose();

        context.setFinalImage(canvas);
        context.setCurrentImage(canvas);
    }
}
