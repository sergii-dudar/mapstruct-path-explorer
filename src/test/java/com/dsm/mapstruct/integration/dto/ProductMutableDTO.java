package com.dsm.mapstruct.integration.dto;

import lombok.Data;

@Data
public class ProductMutableDTO {
    String name;
    String sku;
    double price;
    ProductItem item;

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
