package com.dsm.mapstruct.integration.mapper;

import com.dsm.mapstruct.integration.dto.*;
import com.dsm.mapstruct.testdata.TestClasses.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper interface for testing Java Record path navigation scenarios.
 * Validates that record accessor methods work correctly with MapStruct path expressions.
 */
@Mapper
public interface RecordMapper {

    RecordMapper INSTANCE = Mappers.getMapper(RecordMapper.class);

    // Simple record field
    @Mapping(target = "name", source = "firstName")
    SimpleFieldDTO mapSimpleRecordField(PersonRecord personRecord);

    // Order customer name
    @Mapping(target = "name", source = "customer.firstName")
    SimpleFieldDTO mapOrderCustomerName(OrderRecord orderRecord);

    // Order product name (nested collection)
    @Mapping(target = "productName", source = "items.first.product.name")
    NestedCollectionDTO mapOrderProductName(OrderRecord orderRecord);

    // First item quantity
    @Mapping(target = "age", source = "items.first.quantity")
    PrimitiveDTO mapFirstItemQuantity(OrderRecord orderRecord);

    // Complex order record
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "customerName", source = "customer.firstName")
    @Mapping(target = "customerCity", ignore = true)
    @Mapping(target = "productName", source = "items.first.product.name")
    @Mapping(target = "productPrice", source = "items.first.product.price")
    OrderComplexDTO mapComplexOrderRecord(OrderRecord orderRecord);

    // Record age
    @Mapping(target = "age", source = "age")
    PrimitiveDTO mapRecordAge(PersonRecord personRecord);

    // Person record complete
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "fullName", expression = "java(personRecord.firstName() + \" \" + personRecord.lastName())")
    MixedAccessDTO mapPersonRecordComplete(PersonRecord personRecord);

    // Address record
    @Mapping(target = "city", source = "city")
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "zipCode", source = "zipCode")
    @Mapping(target = "countryName", source = "country.name")
    ComplexNestedDTO mapAddressRecord(AddressRecord addressRecord);

    // Product record
    @Mapping(target = "name", source = "name")
    @Mapping(target = "sku", source = "sku")
    @Mapping(target = "price", source = "price")
    ProductDTO mapProductRecord(ProductRecord productRecord);
}
