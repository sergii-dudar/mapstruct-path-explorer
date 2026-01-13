package com.dsm.mapstruct.integration.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MixedAccessDTO {
    String firstName;
    String lastName;
    String fullName;
}
