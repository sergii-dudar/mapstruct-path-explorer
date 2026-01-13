package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MultipleCollectionDTO {
    String firstOrderId;
    String lastOrderId;
    String firstProductName;
}
