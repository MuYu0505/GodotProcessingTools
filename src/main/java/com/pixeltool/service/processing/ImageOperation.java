package com.pixeltool.service.processing;

public interface ImageOperation {

    boolean supports(ProcessingContext context);

    void apply(ProcessingContext context);
}
