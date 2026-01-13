package com.dsm.mapstruct.integration.dto;

public class MultipleCollectionDTO {
    private String firstOrderId;
    private String lastOrderId;
    private String firstProductName;

    public String getFirstOrderId() {
        return firstOrderId;
    }

    public void setFirstOrderId(String firstOrderId) {
        this.firstOrderId = firstOrderId;
    }

    public String getLastOrderId() {
        return lastOrderId;
    }

    public void setLastOrderId(String lastOrderId) {
        this.lastOrderId = lastOrderId;
    }

    public String getFirstProductName() {
        return firstProductName;
    }

    public void setFirstProductName(String firstProductName) {
        this.firstProductName = firstProductName;
    }
}
