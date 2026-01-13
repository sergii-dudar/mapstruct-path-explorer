package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MultiplePrimitivesDTO {
    int age;
    int quantity;
    double price;
}
