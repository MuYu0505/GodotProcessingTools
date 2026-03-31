package com.pixeltool.dto;

public class ProcessResultItem {

    private String fileName;
    private int originalWidth;
    private int originalHeight;
    private String originalUrl;
    private String cleanedUrl;
    private String transparentUrl;
    private String maskUrl;
    private String finalUrl;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public void setOriginalWidth(int originalWidth) {
        this.originalWidth = originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public void setOriginalHeight(int originalHeight) {
        this.originalHeight = originalHeight;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCleanedUrl() {
        return cleanedUrl;
    }

    public void setCleanedUrl(String cleanedUrl) {
        this.cleanedUrl = cleanedUrl;
    }

    public String getTransparentUrl() {
        return transparentUrl;
    }

    public void setTransparentUrl(String transparentUrl) {
        this.transparentUrl = transparentUrl;
    }

    public String getMaskUrl() {
        return maskUrl;
    }

    public void setMaskUrl(String maskUrl) {
        this.maskUrl = maskUrl;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public void setFinalUrl(String finalUrl) {
        this.finalUrl = finalUrl;
    }
}
