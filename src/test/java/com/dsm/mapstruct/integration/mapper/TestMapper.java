package com.dsm.mapstruct.integration.mapper;

import com.dsm.mapstruct.integration.dto.*;
import com.dsm.mapstruct.testdata.TestClasses.*;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper interface for testing all path navigation scenarios.
 * This mapper validates that the paths suggested by the path explorer tool
 * actually work in real MapStruct mappings.
 */
@Mapper
public interface TestMapper {

    TestMapper INSTANCE = Mappers.getMapper(TestMapper.class);

    // Simple field mapping
    @Mapping(target = "name", source = "firstName")
    SimpleFieldDTO mapSimpleField(Person person);

    // Nested field mapping
    @Mapping(target = "cityName", source = "address.city")
    NestedFieldDTO mapNestedField(Person person);


    // Deeply nested field mapping
    @Mapping(target = "countryCode", source = "address.country.code")
    DeeplyNestedDTO mapDeeplyNested(Person person);

    // Multiple nested paths
    @Mapping(target = "city", source = "address.city")
    @Mapping(target = "state", source = "address.state")
    @Mapping(target = "zipCode", source = "address.zipCode")
    // @Mapping(target = "countryName", source = "address.country.name")
    @Mapping(target = "countryName", expression = "java(person.getAddress().getCountry().getName())")
    ComplexNestedDTO mapComplexNested(Person person);

    // Collection first accessor
    @Mapping(target = "firstOrderId", source = "orders.first.orderId")
    CollectionFirstDTO mapCollectionFirst(Person person);

    // Collection last accessor
    @Mapping(target = "lastOrderId", source = "orders.last.orderId")
    CollectionLastDTO mapCollectionLast(Person person);

    // Nested collection accessor
    @Mapping(target = "productName", source = "orders.first.items.first.product.name")
    NestedCollectionDTO mapNestedCollection(Person person);

    // Collection with nested field
    @Mapping(target = "customerName", source = "orders.first.customer.firstName")
    CollectionNestedFieldDTO mapCollectionNestedField(Person person);

    // Multiple collection accessors
    @Mapping(target = "firstOrderId", source = "orders.first.orderId")
    @Mapping(target = "lastOrderId", source = "orders.last.orderId")
    @Mapping(target = "firstProductName", source = "orders.first.items.first.product.name")
    MultipleCollectionDTO mapMultipleCollections(Person person);

    // Company departments
    @Mapping(target = "departmentName", source = "departments.first.name")
    DepartmentDTO mapDepartmentFirst(Company company);

    // Department head
    @Mapping(target = "headName", source = "departments.first.head.firstName")
    DepartmentHeadDTO mapDepartmentHead(Company company);

    // Department members
    @Mapping(target = "memberLastName", source = "departments.first.members.first.lastName")
    DepartmentMemberDTO mapDepartmentMember(Company company);

    // Getter method mapping
    @Mapping(target = "fullName", source = "fullName")
    GetterMethodDTO mapGetterMethod(Person person);

    // Mixed field and getter
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "fullName", source = "fullName")
    MixedAccessDTO mapMixedAccess(Person person);

    // Order complex mapping
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "customerName", source = "customer.firstName")
    @Mapping(target = "customerCity", source = "customer.address.city")
    @Mapping(target = "productName", source = "items.first.product.name")
    @Mapping(target = "productPrice", source = "items.first.product.price")
    OrderComplexDTO mapOrderComplex(Order order);

    // Primitive mapping
    @Mapping(target = "age", source = "age")
    PrimitiveDTO mapPrimitive(Person person);

    // Multiple primitives
    @Mapping(target = "age", source = "age")
    @Mapping(target = "quantity", source = "orders.first.items.first.quantity")
    @Mapping(target = "price", source = "orders.first.items.first.price")
    MultiplePrimitivesDTO mapMultiplePrimitives(Person person);

    // String concatenation with expression
    @Mapping(target = "fullAddress", expression = "java(person.getAddress() != null ? person.getAddress().getCity() + \", \" + person.getAddress().getState() + \" \" + person.getAddress().getZipCode() : null)")
    ConcatenatedDTO mapConcatenated(Person person);

    // Complete person mapping - comprehensive test
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "age", source = "age")
    @Mapping(target = "street", source = "address.street")
    @Mapping(target = "city", source = "person.address.city")
    @Mapping(target = "state", source = "address.state")
    @Mapping(target = "zipCode", source = "address.zipCode")
    @Mapping(target = "country", source = "address.country.name")
    @Mapping(target = "countryCode", source = "address.country.code")
    @Mapping(target = "firstOrderId", source = "orders.first.orderId")
    @Mapping(target = "firstProductName", source = "orders.first.items.first.product.name")
    CompletePersonDTO mapCompletePerson(Person person);

    @Mapping(target = "address.city", source = "address.city")
    Person toPerson(Person person);

    @Mapping(target = "fullName", source = "person.fullName")
    @Mapping(target = "age", source = "person.age")
    @Mapping(target = "street", source = "person.address.street")
    @Mapping(target = "city", source = "person.address.city")
    @Mapping(target = "state", source = "person.address.state")
    @Mapping(target = "zipCode", source = "person.address.zipCode")
    @Mapping(target = "country", source = "person.address.country.name")
    @Mapping(target = "countryCode", source = "person.address.country.code")
    @Mapping(target = "firstOrderId", source = "person.orders.first.orderId")
    @Mapping(target = "firstProductName", source = "person.orders.first.items.first.product.name")
    CompletePersonDTO mapCompletePerson2(Person person);

    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "age", source = "age")
    @Mapping(target = "street", source = "address.street")
    @Mapping(target = "city", source = "address.city")
    @Mapping(target = "state", source = "address.state")
    @Mapping(target = "zipCode", source = "address.zipCode")
    @Mapping(target = "country", source = "address.country.name")
    @Mapping(target = "countryCode", source = "address.country.code")
    @Mapping(target = "firstOrderId", source = "orders.first.orderId")
    @Mapping(target = "firstProductName", source = "orders.first.items.first.product.name")
    void mapCompletePerson3(@MappingTarget CompletePersonDTO.CompletePersonDTOBuilder dto, Person person);

    @Mapping(target = "fullName", source = "customFullName")
    @Mapping(target = "age", source = "person.age")
    @Mapping(target = "street", source = "person.address.street")
    @Mapping(target = "city", source = "order.customer.address.city")
    @Mapping(target = "state", source = "person.address.state")
    @Mapping(target = "zipCode", source = "person.address.zipCode")
    @Mapping(target = "country", source = "person.address.country.name")
    @Mapping(target = "countryCode", source = "person.address.country.code")
    @Mapping(target = "firstOrderId", source = "person.orders.first.orderId")
    @Mapping(target = "firstProductName", source = "person.orders.first.items.first.product.name")
    CompletePersonDTO mapCompletePerson(Person person, Order order, String customFullName);

    @Mapping(target = "fullName", source = "customFullName")
    @Mapping(target = "age", source = "person.age")
    @Mapping(target = "street", source = "person.address.street")
    @Mapping(target = "city", source = "order.customer.address.city")
    @Mapping(target = "state", source = "person.address.state")
    @Mapping(target = "zipCode", source = "person.address.zipCode")
    @Mapping(target = "country", source = "person.address.country.name")
    @Mapping(target = "countryCode", source = "person.address.country.code")
    @Mapping(target = "firstOrderId", source = "person.orders.first.orderId")
    @Mapping(target = "firstProductName", source = "person.orders.first.items.first.product.name")
    CompletePersonDTO.CompletePersonDTOBuilder mapCompletePerson(@MappingTarget CompletePersonDTO.CompletePersonDTOBuilder dto, Person person, Order order, String customFullName);

    // Enum mapping for @ValueMapping completion testing
    @ValueMapping(target = "PENDING", source = "NEW")
    @ValueMapping(target = "CONFIRMED", source = "PROCESSING")
    @ValueMapping(target = "SHIPPED", source = "IN_TRANSIT")
    @ValueMapping(target = "DELIVERED", source = "COMPLETED")
    @ValueMapping(target = "CANCELLED", source = "REJECTED")
    OrderStatus fromStateTyped(OrderState state);

    @ValueMapping(target = "PENDING_TEST", source = "NEW")
    @ValueMapping(target = "CONFIRMED_TEST", source = "PROCESSING")
    @ValueMapping(target = "SHIPPED_TEST", source = "IN_TRANSIT")
    @ValueMapping(target = "DELIVERED_TEST", source = "COMPLETED")
    @ValueMapping(target = "CANCELLED_TEST", source = "REJECTED")
    String toStringState(OrderState state);

    @ValueMapping(target = "PENDING", source = "NEW")
    @ValueMapping(target = "CONFIRMED", source = "PROCESSING")
    @ValueMapping(target = "SHIPPED", source = "IN_TRANSIT")
    @ValueMapping(target = "DELIVERED", source = "COMPLETED")
    @ValueMapping(target = "CANCELLED", source = "REJECTED")
    @ValueMapping(target = MappingConstants.THROW_EXCEPTION, source = MappingConstants.ANY_UNMAPPED)
    OrderStatus fromStatusString(String state);
}
