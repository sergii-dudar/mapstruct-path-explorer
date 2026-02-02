package com.dsm.mapstruct.integration.dto;

import lombok.Data;

@Data
public class ProductMutableDTO {
    String name;
    String sku;
    double price;
    ProductItem item;

    public String iban;
    public String sPerson;

    public String getIBAN() {
        return iban;
    }

    public void setIBAN(String iban) {
        this.iban = iban;
    }

    @Data
    public static class ProductItem {
        String itemName;
        String price;
        ProductItemDetails details;
    }

    @Data
    public static class ProductItemDetails {
        String detailName;
        String message;
    }
}
