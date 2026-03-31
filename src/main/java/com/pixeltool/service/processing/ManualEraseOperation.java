package com.pixeltool.service.processing;

import com.pixeltool.util.ImageSupport;
import org.springframework.stereotype.Component;

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
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (mask.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > 10) {
                    target.setRGB(x, y, 0x00000000);
                }
            }
        }

        context.setCurrentImage(target);
    }
}
