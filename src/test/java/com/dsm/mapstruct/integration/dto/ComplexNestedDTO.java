package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ComplexNestedDTO {
    String city;
    String state;
    String zipCode;
    String countryName;
}
