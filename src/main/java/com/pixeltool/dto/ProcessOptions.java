package com.pixeltool.dto;

public class ProcessOptions {

    private boolean removeWatermark = true;
    private boolean removeBackground = true;
    private boolean extractMask = true;
    private boolean resizeEnabled = true;
    private int targetWidth = 64;
    private int targetHeight = 64;
    private int backgroundTolerance = 28;
    private int watermarkTolerance = 60;
    private int alphaThreshold = 10;
    private String watermarkCorner = "AUTO";

    public boolean isRemoveWatermark() {
        return removeWatermark;
    }

    public void setRemoveWatermark(boolean removeWatermark) {
        this.removeWatermark = removeWatermark;
    }

    public boolean isRemoveBackground() {
        return removeBackground;
    }

    public void setRemoveBackground(boolean removeBackground) {
        this.removeBackground = removeBackground;
    }

    public boolean isExtractMask() {
        return extractMask;
    }

    public void setExtractMask(boolean extractMask) {
        this.extractMask = extractMask;
    }

    public boolean isResizeEnabled() {
        return resizeEnabled;
    }

    public void setResizeEnabled(boolean resizeEnabled) {
        this.resizeEnabled = resizeEnabled;
    }

    public int getTargetWidth() {
        return targetWidth;
    }

    public void setTargetWidth(int targetWidth) {
        this.targetWidth = targetWidth;
    }

    public int getTargetHeight() {
        return targetHeight;
    }

    public void setTargetHeight(int targetHeight) {
        this.targetHeight = targetHeight;
    }

    public int getBackgroundTolerance() {
        return backgroundTolerance;
    }

    public void setBackgroundTolerance(int backgroundTolerance) {
        this.backgroundTolerance = backgroundTolerance;
    }

    public int getWatermarkTolerance() {
        return watermarkTolerance;
    }

    public void setWatermarkTolerance(int watermarkTolerance) {
        this.watermarkTolerance = watermarkTolerance;
    }

    public int getAlphaThreshold() {
        return alphaThreshold;
    }

    public void setAlphaThreshold(int alphaThreshold) {
        this.alphaThreshold = alphaThreshold;
    }

    public String getWatermarkCorner() {
        return watermarkCorner;
    }

    public void setWatermarkCorner(String watermarkCorner) {
        this.watermarkCorner = watermarkCorner;
    }
}
