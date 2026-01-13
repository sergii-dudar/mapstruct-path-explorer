package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductDTO {
    String name;
    String sku;
    double price;
}
