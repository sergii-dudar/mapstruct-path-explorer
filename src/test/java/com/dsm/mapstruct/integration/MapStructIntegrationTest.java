package com.dsm.mapstruct.integration;

import com.dsm.mapstruct.integration.dto.*;
import com.dsm.mapstruct.integration.mapper.TestMapper;
import com.dsm.mapstruct.testdata.TestClasses.*;
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
@DisplayName("MapStruct Integration Tests - Real Mapper Validation")
class MapStructIntegrationTest {

    private TestMapper mapper;
    private Person testPerson;
    private Company testCompany;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(TestMapper.class);
        setupTestData();
    }

    private void setupTestData() {
        // Create test country
        Country usa = new Country();
        usa.name = "United States";
        usa.code = "US";

        // Create test address
        Address address = new Address();
        address.street = "123 Main St";
        address.city = "Springfield";
        address.state = "IL";
        address.zipCode = "62701";
        address.country = usa;

        // Create test product
        Product product = new Product();
        product.name = "Laptop";
        product.sku = "SKU-001";
        product.price = 999.99;

        // Create order item
        OrderItem orderItem = new OrderItem();
        orderItem.product = product;
        orderItem.quantity = 2;
        orderItem.price = 1999.98;

        // Create person
        testPerson = new Person();
        testPerson.firstName = "John";
        testPerson.lastName = "Doe";
        testPerson.age = 30;
        testPerson.address = address;

        // Create order
        testOrder = new Order();
        testOrder.orderId = "ORD-123";
        testOrder.items = Arrays.asList(orderItem);
        testOrder.customer = testPerson;

        testPerson.orders = Arrays.asList(testOrder);

        // Create company with employees
        Person employee1 = new Person();
        employee1.firstName = "Alice";
        employee1.lastName = "Smith";
        employee1.age = 28;

        Person employee2 = new Person();
        employee2.firstName = "Bob";
        employee2.lastName = "Johnson";
        employee2.age = 35;

        Department department = new Department();
        department.name = "Engineering";
        department.head = employee1;
        department.members = Arrays.asList(employee1, employee2);

        testCompany = new Company();
        testCompany.name = "Acme Corp";
        testCompany.employees = new Person[]{employee1, employee2};
        testCompany.departments = Arrays.asList(department);
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
        Person personWithoutAddress = new Person();
        personWithoutAddress.firstName = "Jane";
        personWithoutAddress.lastName = "Smith";
        personWithoutAddress.age = 25;
        personWithoutAddress.address = null;

        // Test path: address.city (address is null)
        NestedFieldDTO result = mapper.mapNestedField(personWithoutAddress);

        assertThat(result).isNotNull();
        assertThat(result.getCityName()).isNull();
    }

    @Test
    @DisplayName("Null safety - empty collection throws exception")
    void testNullSafetyEmptyCollection() {
        Person personWithoutOrders = new Person();
        personWithoutOrders.firstName = "Jane";
        personWithoutOrders.lastName = "Smith";
        personWithoutOrders.age = 25;
        personWithoutOrders.orders = List.of();

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
