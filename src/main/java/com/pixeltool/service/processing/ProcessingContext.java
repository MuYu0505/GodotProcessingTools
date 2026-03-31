package com.pixeltool.service.processing;

import com.pixeltool.dto.ImageEditOptions;
import com.pixeltool.dto.ProcessOptions;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class ProcessingContext {

    private final ProcessOptions options;
    private final ImageEditOptions editOptions;
    private final BufferedImage originalImage;
    private final BufferedImage croppedOriginalImage;
    private BufferedImage currentImage;
    private BufferedImage preparedImage;
    private BufferedImage transparentImage;
    private BufferedImage maskImage;
    private BufferedImage finalImage;
    private BufferedImage eraseMask;
    private Color estimatedBackground;

    public ProcessingContext(ProcessOptions options, ImageEditOptions editOptions,
                             BufferedImage originalImage, BufferedImage croppedOriginalImage) {
        this.options = options;
        this.editOptions = editOptions;
        this.originalImage = originalImage;
        this.croppedOriginalImage = croppedOriginalImage;
        this.currentImage = croppedOriginalImage;
    }

    public ProcessOptions getOptions() {
        return options;
    }

    public BufferedImage getOriginalImage() {
        return originalImage;
    }

    public ImageEditOptions getEditOptions() {
        return editOptions;
    }

    public BufferedImage getCroppedOriginalImage() {
        return croppedOriginalImage;
    }

    public BufferedImage getCurrentImage() {
        return currentImage;
    }

    public void setCurrentImage(BufferedImage currentImage) {
        this.currentImage = currentImage;
    }

    public BufferedImage getPreparedImage() {
        return preparedImage;
    }

    public void setPreparedImage(BufferedImage preparedImage) {
        this.preparedImage = preparedImage;
    }

    public BufferedImage getEraseMask() {
        return eraseMask;
    }

    public void setEraseMask(BufferedImage eraseMask) {
        this.eraseMask = eraseMask;
    }

    public BufferedImage getTransparentImage() {
        return transparentImage;
    }

    public void setTransparentImage(BufferedImage transparentImage) {
        this.transparentImage = transparentImage;
    }

    public BufferedImage getMaskImage() {
        return maskImage;
    }

    public void setMaskImage(BufferedImage maskImage) {
        this.maskImage = maskImage;
    }

    public BufferedImage getFinalImage() {
        return finalImage;
    }

    public void setFinalImage(BufferedImage finalImage) {
        this.finalImage = finalImage;
    }

    public Color getEstimatedBackground() {
        return estimatedBackground;
    }

    public void setEstimatedBackground(Color estimatedBackground) {
        this.estimatedBackground = estimatedBackground;
    }
}
