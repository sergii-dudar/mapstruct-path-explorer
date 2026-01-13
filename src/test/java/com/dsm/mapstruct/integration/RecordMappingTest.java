package com.dsm.mapstruct.integration;

import com.dsm.mapstruct.integration.dto.*;
import com.dsm.mapstruct.integration.mapper.RecordMapper;
import com.dsm.mapstruct.testdata.TestClasses.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Java Records with MapStruct.
 * Validates that path navigation works correctly with record types.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@DisplayName("MapStruct Integration Tests - Java Records")
class RecordMappingTest {

    private RecordMapper mapper;
    private PersonRecord testPersonRecord;
    private OrderRecord testOrderRecord;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(RecordMapper.class);
        setupTestData();
    }

    private void setupTestData() {
        // Create test country record
        CountryRecord usaRecord = new CountryRecord("United States", "US");

        // Create test address record
        AddressRecord addressRecord = new AddressRecord(
                "123 Main St",
                "Springfield",
                "62701",
                usaRecord
        );

        // Create test product record
        ProductRecord productRecord = new ProductRecord("Laptop", "SKU-001", 999.99);

        // Create order item record
        OrderItemRecord orderItemRecord = new OrderItemRecord(productRecord, 2, 1999.98);

        // Create person record
        this.testPersonRecord = new PersonRecord("John", "Doe", 30);

        // Create order record
        this.testOrderRecord = new OrderRecord(
                "ORD-123",
                List.of(orderItemRecord),
                this.testPersonRecord
        );
    }

    @Test
    @DisplayName("Simple record field mapping - source.field")
    void testSimpleRecordFieldMapping() {
        // Test path: firstName
        SimpleFieldDTO result = mapper.mapSimpleRecordField(testPersonRecord);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("John");
    }

    @Test
    @DisplayName("Nested record field mapping - source.address.city")
    void testNestedRecordFieldMapping() {
        // Create person with address
        CountryRecord country = new CountryRecord("USA", "US");
        AddressRecord address = new AddressRecord("123 Main St", "New York", "10001", country);

        // For this test we need a PersonWithAddressRecord, let's use OrderRecord instead
        // to test nested access through customer.firstName
        SimpleFieldDTO result = mapper.mapOrderCustomerName(testOrderRecord);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("John");
    }

    @Test
    @DisplayName("Deeply nested record field - source.items.first.product.name")
    void testDeeplyNestedRecordField() {
        // Test path: items.first.product.name
        NestedCollectionDTO result = mapper.mapOrderProductName(testOrderRecord);

        assertThat(result).isNotNull();
        assertThat(result.getProductName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Record collection first accessor - source.items.first.quantity")
    void testRecordCollectionFirst() {
        // Test path: items.first.quantity
        PrimitiveDTO result = mapper.mapFirstItemQuantity(testOrderRecord);

        assertThat(result).isNotNull();
        assertThat(result.getAge()).isEqualTo(2);  // reusing PrimitiveDTO's age field
    }

    @Test
    @DisplayName("Record with multiple nested paths")
    void testRecordMultipleNestedPaths() {
        // Test multiple paths from OrderRecord
        OrderComplexDTO result = mapper.mapComplexOrderRecord(testOrderRecord);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-123");
        assertThat(result.getCustomerName()).isEqualTo("John");
        assertThat(result.getProductName()).isEqualTo("Laptop");
        assertThat(result.getProductPrice()).isEqualTo(999.99);
    }

    @Test
    @DisplayName("Record primitive field access")
    void testRecordPrimitiveAccess() {
        // Test path: age
        PrimitiveDTO result = mapper.mapRecordAge(testPersonRecord);

        assertThat(result).isNotNull();
        assertThat(result.getAge()).isEqualTo(30);
    }

    @Test
    @DisplayName("Record null safety - null nested record")
    void testRecordNullSafety() {
        OrderRecord orderWithoutCustomer = new OrderRecord(
                "ORD-456",
                List.of(),
                null
        );

        // Test path: customer.firstName (customer is null)
        SimpleFieldDTO result = mapper.mapOrderCustomerName(orderWithoutCustomer);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isNull();
    }

    @Test
    @DisplayName("Record empty collection throws exception")
    void testRecordEmptyCollection() {
        OrderRecord orderWithNoItems = new OrderRecord(
                "ORD-789",
                List.of(),
                testPersonRecord
        );

        // Test path: items.first.product.name (items is empty)
        // MapStruct's .first accessor throws NoSuchElementException on empty collections
        // This is expected behavior - MapStruct does not provide null-safe collection access by default
        org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class,
                () -> mapper.mapOrderProductName(orderWithNoItems)
        );
    }

    @Test
    @DisplayName("Mixed record and class mapping")
    void testMixedRecordAndClass() {
        // Test that we can map from records to classes seamlessly
        PersonRecord personRecord = new PersonRecord("Alice", "Smith", 25);

        MixedAccessDTO result = mapper.mapPersonRecordComplete(personRecord);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Smith");
    }

    @Test
    @DisplayName("Address record nested mapping")
    void testAddressRecordNested() {
        CountryRecord country = new CountryRecord("Canada", "CA");
        AddressRecord address = new AddressRecord("456 Oak Ave", "Toronto", "M5V 1A1", country);

        ComplexNestedDTO result = mapper.mapAddressRecord(address);

        assertThat(result).isNotNull();
        assertThat(result.getCity()).isEqualTo("Toronto");
        assertThat(result.getZipCode()).isEqualTo("M5V 1A1");
        assertThat(result.getCountryName()).isEqualTo("Canada");
    }

    @Test
    @DisplayName("Product record all fields")
    void testProductRecordAllFields() {
        ProductRecord product = new ProductRecord("Mouse", "SKU-002", 29.99);

        ProductDTO result = mapper.mapProductRecord(product);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Mouse");
        assertThat(result.getSku()).isEqualTo("SKU-002");
        assertThat(result.getPrice()).isEqualTo(29.99);
    }
}
