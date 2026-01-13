package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CompletePersonDTO {
    String fullName;
    int age;
    String street;
    String city;
    String state;
    String zipCode;
    String country;
    String countryCode;
    String firstOrderId;
    String firstProductName;
}
