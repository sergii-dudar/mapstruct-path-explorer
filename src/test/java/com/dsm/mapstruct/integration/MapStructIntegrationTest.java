package com.dsm.mapstruct.integration;

import com.dsm.mapstruct.integration.dto.*;
import com.dsm.mapstruct.integration.mapper.TestMapper;
import com.dsm.mapstruct.testdata.TestClasses.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify MapStruct path expressions work correctly
 * with real MapStruct mappers for all path navigation scenarios.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@DisplayName("MapStruct Integration Tests - Real Mapper Validation")
class MapStructIntegrationTest {

    TestMapper mapper;
    Person testPerson;
    Company testCompany;
    Order testOrder;

    @BeforeEach
    void setUp() {
        this.mapper = Mappers.getMapper(TestMapper.class);
        setupTestData();
    }

    private void setupTestData() {
        // Create test country
        Country usa = Country.builder()
                .name("United States")
                .code("US")
                .build();

        // Create test address
        Address address = Address.builder()
                .street("123 Main St")
                .city("Springfield")
                .state("IL")
                .zipCode("62701")
                .country(usa)
                .build();

        // Create test product
        Product product = Product.builder()
                .name("Laptop")
                .sku("SKU-001")
                .price(999.99)
                .build();

        // Create order item
        OrderItem orderItem = OrderItem.builder()
                .product(product)
                .quantity(2)
                .price(1999.98)
                .build();

        // Create person
        this.testPerson = Person.builder()
                .firstName("John")
                .lastName("Doe")
                .age(30)
                .address(address)
                .build();

        // Create order
        this.testOrder = Order.builder()
                .orderId("ORD-123")
                .items(Arrays.asList(orderItem))
                .customer(this.testPerson)
                .build();

        this.testPerson = this.testPerson.toBuilder()
                .orders(Arrays.asList(this.testOrder))
                .build();

        // Create company with employees
        Person employee1 = Person.builder()
                .firstName("Alice")
                .lastName("Smith")
                .age(28)
                .build();

        Person employee2 = Person.builder()
                .firstName("Bob")
                .lastName("Johnson")
                .age(35)

                .build();
        Department department = Department.builder()
                .name("Engineering")
                .head(employee1)
                .members(Arrays.asList(employee1, employee2))
                .build();

        this.testCompany = Company.builder()
                .name("Acme Corp")
                .employees(new Person[]{employee1, employee2})
                .departments(Arrays.asList(department))
                .build();
    }

    @Test
    @DisplayName("Simple field mapping - source.field")
    void testSimpleFieldMapping() {
        // Test path: firstName
        SimpleFieldDTO result = mapper.mapSimpleField(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("John");
    }

    @Test
    @DisplayName("Nested field mapping - source.address.city")
    void testNestedFieldMapping() {
        // Test path: address.city
        NestedFieldDTO result = mapper.mapNestedField(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getCityName()).isEqualTo("Springfield");
    }

    @Test
    @DisplayName("Deeply nested field mapping - source.address.country.code")
    void testDeeplyNestedFieldMapping() {
        // Test path: address.country.code
        DeeplyNestedDTO result = mapper.mapDeeplyNested(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getCountryCode()).isEqualTo("US");
    }

    @Test
    @DisplayName("Multiple nested paths in single mapper")
    void testMultipleNestedPaths() {
        // Test multiple paths: address.city, address.state, address.zipCode
        ComplexNestedDTO result = mapper.mapComplexNested(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getCity()).isEqualTo("Springfield");
        assertThat(result.getState()).isEqualTo("IL");
        assertThat(result.getZipCode()).isEqualTo("62701");
        assertThat(result.getCountryName()).isEqualTo("United States");
    }

    @Test
    @DisplayName("Collection first accessor - source.orders.first.orderId")
    void testCollectionFirstAccessor() {
        // Test path: orders.first.orderId
        CollectionFirstDTO result = mapper.mapCollectionFirst(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getFirstOrderId()).isEqualTo("ORD-123");
    }

    @Test
    @DisplayName("Collection last accessor - source.orders.last.orderId")
    void testCollectionLastAccessor() {
        // Test path: orders.last.orderId
        CollectionLastDTO result = mapper.mapCollectionLast(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getLastOrderId()).isEqualTo("ORD-123");
    }

    @Test
    @DisplayName("Nested collection accessor - source.orders.first.items.first.product.name")
    void testNestedCollectionAccessor() {
        // Test path: orders.first.items.first.product.name
        NestedCollectionDTO result = mapper.mapNestedCollection(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getProductName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Collection with nested field - source.orders.first.customer.firstName")
    void testCollectionWithNestedField() {
        // Test path: orders.first.customer.firstName
        CollectionNestedFieldDTO result = mapper.mapCollectionNestedField(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("John");
    }

    @Test
    @DisplayName("Multiple collection accessors in single mapper")
    void testMultipleCollectionAccessors() {
        // Test multiple collection paths
        MultipleCollectionDTO result = mapper.mapMultipleCollections(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getFirstOrderId()).isEqualTo("ORD-123");
        assertThat(result.getLastOrderId()).isEqualTo("ORD-123");
        assertThat(result.getFirstProductName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Company departments collection - source.departments.first.name")
    void testCompanyDepartments() {
        // Test path: departments.first.name
        DepartmentDTO result = mapper.mapDepartmentFirst(testCompany);

        assertThat(result).isNotNull();
        assertThat(result.getDepartmentName()).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("Department head nested path - source.departments.first.head.firstName")
    void testDepartmentHead() {
        // Test path: departments.first.head.firstName
        DepartmentHeadDTO result = mapper.mapDepartmentHead(testCompany);

        assertThat(result).isNotNull();
        assertThat(result.getHeadName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Department members collection - source.departments.first.members.first.lastName")
    void testDepartmentMembers() {
        // Test path: departments.first.members.first.lastName
        DepartmentMemberDTO result = mapper.mapDepartmentMember(testCompany);

        assertThat(result).isNotNull();
        assertThat(result.getMemberLastName()).isEqualTo("Smith");
    }

    @Test
    @DisplayName("Getter method mapping - source.fullName")
    void testGetterMapping() {
        // Test path: fullName (getter method without field)
        GetterMethodDTO result = mapper.mapGetterMethod(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Mixed field and getter paths")
    void testMixedFieldAndGetter() {
        // Test paths: firstName (field), lastName (field), fullName (getter)
        MixedAccessDTO result = mapper.mapMixedAccess(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Order with multiple nested paths")
    void testOrderComplexMapping() {
        // Test various paths from Order
        OrderComplexDTO result = mapper.mapOrderComplex(testOrder);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-123");
        assertThat(result.getCustomerName()).isEqualTo("John");
        assertThat(result.getCustomerCity()).isEqualTo("Springfield");
        assertThat(result.getProductName()).isEqualTo("Laptop");
        assertThat(result.getProductPrice()).isEqualTo(999.99);
    }

    @Test
    @DisplayName("Null safety - null nested object")
    void testNullSafety() {
        Person personWithoutAddress = Person.builder()
                .firstName("Jane")
                .lastName("Smith")
                .age(25)
                .build();

        // Test path: address.city (address is null)
        NestedFieldDTO result = mapper.mapNestedField(personWithoutAddress);

        assertThat(result).isNotNull();
        assertThat(result.getCityName()).isNull();
    }

    @Test
    @DisplayName("Null safety - empty collection throws exception")
    void testNullSafetyEmptyCollection() {
        Person personWithoutOrders = Person.builder()
                .firstName("Jane")
                .lastName("Smith")
                .age(25)
                .orders(List.of())
                .build();

        // Test path: orders.first.orderId (orders is empty)
        // MapStruct's .first accessor throws NoSuchElementException on empty collections
        // This is expected behavior - MapStruct does not provide null-safe collection access by default
        org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class,
                () -> mapper.mapCollectionFirst(personWithoutOrders)
        );
    }

    @Test
    @DisplayName("Primitive type mapping - source.age")
    void testPrimitiveMapping() {
        // Test path: age (primitive int)
        PrimitiveDTO result = mapper.mapPrimitive(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getAge()).isEqualTo(30);
    }

    @Test
    @DisplayName("Multiple primitive paths")
    void testMultiplePrimitives() {
        // Test paths: age, orders.first.items.first.quantity, orders.first.items.first.price
        MultiplePrimitivesDTO result = mapper.mapMultiplePrimitives(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getAge()).isEqualTo(30);
        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getPrice()).isEqualTo(1999.98);
    }

    @Test
    @DisplayName("String concatenation with nested paths")
    void testStringConcatenation() {
        // Test expression combining multiple paths
        ConcatenatedDTO result = mapper.mapConcatenated(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getFullAddress())
                .contains("Springfield")
                .contains("IL")
                .contains("62701");
    }

    @Test
    @DisplayName("Complex real-world scenario")
    void testComplexRealWorldScenario() {
        // Test comprehensive real-world mapping
        CompletePersonDTO result = mapper.mapCompletePerson(testPerson);

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("John Doe");
        assertThat(result.getAge()).isEqualTo(30);
        assertThat(result.getStreet()).isEqualTo("123 Main St");
        assertThat(result.getCity()).isEqualTo("Springfield");
        assertThat(result.getState()).isEqualTo("IL");
        assertThat(result.getZipCode()).isEqualTo("62701");
        assertThat(result.getCountry()).isEqualTo("United States");
        assertThat(result.getCountryCode()).isEqualTo("US");
        assertThat(result.getFirstOrderId()).isEqualTo("ORD-123");
        assertThat(result.getFirstProductName()).isEqualTo("Laptop");
    }
}
