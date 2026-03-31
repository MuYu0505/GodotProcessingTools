package com.pixeltool.dto;

public class ImageEditOptions {

    private Integer cropX;
    private Integer cropY;
    private Integer cropWidth;
    private Integer cropHeight;
    private Integer sampleX;
    private Integer sampleY;
    private java.util.List<java.util.Map<String, Object>> samplePoints;
    private String eraseMaskDataUrl;

    public java.util.List<java.util.Map<String, Object>> getSamplePoints() {
        return samplePoints;
    }

    public void setSamplePoints(java.util.List<java.util.Map<String, Object>> samplePoints) {
        this.samplePoints = samplePoints;
    }

    public Integer getCropX() {
        return cropX;
    }

    public void setCropX(Integer cropX) {
        this.cropX = cropX;
    }

    public Integer getCropY() {
        return cropY;
    }

    public void setCropY(Integer cropY) {
        this.cropY = cropY;
    }

    public Integer getCropWidth() {
        return cropWidth;
    }

    public void setCropWidth(Integer cropWidth) {
        this.cropWidth = cropWidth;
    }

    public Integer getCropHeight() {
        return cropHeight;
    }

    public void setCropHeight(Integer cropHeight) {
        this.cropHeight = cropHeight;
    }

    public Integer getSampleX() {
        return sampleX;
    }

    public void setSampleX(Integer sampleX) {
        this.sampleX = sampleX;
    }

    public Integer getSampleY() {
        return sampleY;
    }

    public void setSampleY(Integer sampleY) {
        this.sampleY = sampleY;
    }

    public String getEraseMaskDataUrl() {
        return eraseMaskDataUrl;
    }

    public void setEraseMaskDataUrl(String eraseMaskDataUrl) {
        this.eraseMaskDataUrl = eraseMaskDataUrl;
    }
}
