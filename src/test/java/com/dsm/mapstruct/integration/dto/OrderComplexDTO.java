package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderComplexDTO {
    String orderId;
    String customerName;
    String customerCity;
    String productName;
    double productPrice;
}
