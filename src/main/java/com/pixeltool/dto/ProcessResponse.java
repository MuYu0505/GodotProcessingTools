package com.pixeltool.dto;

import java.util.ArrayList;
import java.util.List;

public class ProcessResponse {

    private String jobId;
    private String downloadUrl;
    private List<ProcessResultItem> items = new ArrayList<ProcessResultItem>();

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public List<ProcessResultItem> getItems() {
        return items;
    }

    public void setItems(List<ProcessResultItem> items) {
        this.items = items;
    }
}
