package com.pixeltool.dto;

public class PreviewResponse {

    private int width;
    private int height;
    private String croppedUrl;
    private String cleanedUrl;
    private String transparentUrl;
    private String maskUrl;
    private String finalUrl;

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getCroppedUrl() {
        return croppedUrl;
    }

    public void setCroppedUrl(String croppedUrl) {
        this.croppedUrl = croppedUrl;
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
