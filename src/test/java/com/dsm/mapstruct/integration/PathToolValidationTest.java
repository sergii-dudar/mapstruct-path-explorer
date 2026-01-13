package com.dsm.mapstruct.integration;

import com.dsm.mapstruct.PathNavigator;
import com.dsm.mapstruct.integration.mapper.TestMapper;
import com.dsm.mapstruct.model.CompletionResult;
import com.dsm.mapstruct.model.FieldInfo;
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
 * Validates that paths suggested by the PathNavigator (used by MapStructPathTool) actually work
 * in real MapStruct mappings. This is the critical test that ensures the tool's output is correct.
 *
 * Test Flow:
 * 1. Use PathNavigator to get path suggestions for a class
 * 2. Verify expected paths are in the suggestions
 * 3. Verify those paths actually work in MapStruct mappings
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@DisplayName("Path Tool Validation - Tool Output Works with MapStruct")
class PathToolValidationTest {

    PathNavigator pathNavigator;
    TestMapper mapper;
    Person testPerson;
    Company testCompany;

    @BeforeEach
    void setUp() {
        this.pathNavigator = new PathNavigator();
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

        // Create order
        Order testOrder = Order.builder()
                .orderId("ORD-123")
                .items(Arrays.asList(orderItem))
                .customer(testPerson)
                .build();

        // Create person
        testPerson = Person.builder()
                .firstName("John")
                .lastName("Doe")
                .age(30)
                .address(address)
                .orders(Arrays.asList(testOrder))
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
    @DisplayName("Tool suggests 'firstName' and it works in MapStruct")
    void testSimpleFieldSuggestion() {
        // Step 1: Get suggestions from tool for Person class
        CompletionResult result = pathNavigator.navigate(Person.class, "");

        // Step 2: Verify 'firstName' is in suggestions
        assertThat(result.completions()).isNotEmpty();
        boolean hasFirstName = result.completions().stream()
                .anyMatch(f -> f.name().equals("firstName"));
        assertThat(hasFirstName)
                .withFailMessage("Tool should suggest 'firstName' field")
                .isTrue();

        // Step 3: Verify the path 'firstName' works in MapStruct
        // Using mapper: @Mapping(target = "name", source = "firstName")
        var dto = mapper.mapSimpleField(testPerson);
        assertThat(dto.getName()).isEqualTo("John");
    }

    @Test
    @DisplayName("Tool suggests 'address' -> 'city' and path works in MapStruct")
    void testNestedPathSuggestion() {
        // Step 1: Get suggestions for 'address.'
        CompletionResult result = pathNavigator.navigate(Person.class, "address.");

        // Step 2: Verify 'city' is in suggestions
        assertThat(result.completions()).isNotEmpty();
        boolean hasCity = result.completions().stream()
                .anyMatch(f -> f.name().equals("city"));
        assertThat(hasCity)
                .withFailMessage("Tool should suggest 'city' field after 'address.'")
                .isTrue();

        // Step 3: Verify the complete path 'address.city' works in MapStruct
        // Using mapper: @Mapping(target = "cityName", source = "address.city")
        var dto = mapper.mapNestedField(testPerson);
        assertThat(dto.getCityName()).isEqualTo("Springfield");
    }

    @Test
    @DisplayName("Tool suggests 'address.country.code' path and it works")
    void testDeeplyNestedPathSuggestion() {
        // Step 1: Get suggestions for 'address.country.'
        CompletionResult result = pathNavigator.navigate(Person.class, "address.country.");

        // Step 2: Verify 'code' is in suggestions
        assertThat(result.completions()).isNotEmpty();
        boolean hasCode = result.completions().stream()
                .anyMatch(f -> f.name().equals("code"));
        assertThat(hasCode)
                .withFailMessage("Tool should suggest 'code' field after 'address.country.'")
                .isTrue();

        // Step 3: Verify the complete path 'address.country.code' works in MapStruct
        // Using mapper: @Mapping(target = "countryCode", source = "address.country.code")
        var dto = mapper.mapDeeplyNested(testPerson);
        assertThat(dto.getCountryCode()).isEqualTo("US");
    }

    @Test
    @DisplayName("Tool suggests 'orders.first' collection accessor and it works")
    void testCollectionFirstSuggestion() {
        // Step 1: Get suggestions for 'orders.'
        CompletionResult result = pathNavigator.navigate(Person.class, "orders.");

        // Step 2: Verify 'first' is in suggestions (MapStruct collection property)
        assertThat(result.completions()).isNotEmpty();
        boolean hasFirst = result.completions().stream()
                .anyMatch(f -> f.name().equals("first"));
        assertThat(hasFirst)
                .withFailMessage("Tool should suggest 'first' collection accessor after 'orders.'")
                .isTrue();

        // Step 3: Get suggestions after 'orders.first.'
        CompletionResult afterFirst = pathNavigator.navigate(Person.class, "orders.first.");
        boolean hasOrderId = afterFirst.completions().stream()
                .anyMatch(f -> f.name().equals("orderId"));
        assertThat(hasOrderId)
                .withFailMessage("Tool should suggest 'orderId' after 'orders.first.'")
                .isTrue();

        // Step 4: Verify the complete path 'orders.first.orderId' works in MapStruct
        // Using mapper: @Mapping(target = "firstOrderId", source = "orders.first.orderId")
        var dto = mapper.mapCollectionFirst(testPerson);
        assertThat(dto.getFirstOrderId()).isEqualTo("ORD-123");
    }

    @Test
    @DisplayName("Tool suggests 'orders.first.items.first.product.name' nested collection path and it works")
    void testNestedCollectionPathSuggestion() {
        // Build path step by step using tool suggestions

        // Step 1: orders.
        CompletionResult step1 = pathNavigator.navigate(Person.class, "orders.");
        assertThat(step1.completions().stream().anyMatch(f -> f.name().equals("first")))
                .withFailMessage("Tool should suggest 'first' after 'orders.'")
                .isTrue();

        // Step 2: orders.first.
        CompletionResult step2 = pathNavigator.navigate(Person.class, "orders.first.");
        assertThat(step2.completions().stream().anyMatch(f -> f.name().equals("items")))
                .withFailMessage("Tool should suggest 'items' after 'orders.first.'")
                .isTrue();

        // Step 3: orders.first.items.
        CompletionResult step3 = pathNavigator.navigate(Person.class, "orders.first.items.");
        assertThat(step3.completions().stream().anyMatch(f -> f.name().equals("first")))
                .withFailMessage("Tool should suggest 'first' after 'orders.first.items.'")
                .isTrue();

        // Step 4: orders.first.items.first.
        CompletionResult step4 = pathNavigator.navigate(Person.class, "orders.first.items.first.");
        assertThat(step4.completions().stream().anyMatch(f -> f.name().equals("product")))
                .withFailMessage("Tool should suggest 'product' after 'orders.first.items.first.'")
                .isTrue();

        // Step 5: orders.first.items.first.product.
        CompletionResult step5 = pathNavigator.navigate(Person.class, "orders.first.items.first.product.");
        assertThat(step5.completions().stream().anyMatch(f -> f.name().equals("name")))
                .withFailMessage("Tool should suggest 'name' after 'orders.first.items.first.product.'")
                .isTrue();

        // Step 6: Verify the complete path works in MapStruct
        // Using mapper: @Mapping(target = "productName", source = "orders.first.items.first.product.name")
        var dto = mapper.mapNestedCollection(testPerson);
        assertThat(dto.getProductName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Tool suggests 'departments.first.head.firstName' and it works")
    void testDepartmentHeadPathSuggestion() {
        // Build path step by step

        // Step 1: departments.
        CompletionResult step1 = pathNavigator.navigate(Company.class, "departments.");
        assertThat(step1.completions().stream().anyMatch(f -> f.name().equals("first")))
                .withFailMessage("Tool should suggest 'first' after 'departments.'")
                .isTrue();

        // Step 2: departments.first.
        CompletionResult step2 = pathNavigator.navigate(Company.class, "departments.first.");
        assertThat(step2.completions().stream().anyMatch(f -> f.name().equals("head")))
                .withFailMessage("Tool should suggest 'head' after 'departments.first.'")
                .isTrue();

        // Step 3: departments.first.head.
        CompletionResult step3 = pathNavigator.navigate(Company.class, "departments.first.head.");
        assertThat(step3.completions().stream().anyMatch(f -> f.name().equals("firstName")))
                .withFailMessage("Tool should suggest 'firstName' after 'departments.first.head.'")
                .isTrue();

        // Step 4: Verify the complete path works in MapStruct
        // Using mapper: @Mapping(target = "headName", source = "departments.first.head.firstName")
        var dto = mapper.mapDepartmentHead(testCompany);
        assertThat(dto.getHeadName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Tool suggests getter method 'fullName' and it works")
    void testGetterMethodSuggestion() {
        // Step 1: Get suggestions from tool
        CompletionResult result = pathNavigator.navigate(Person.class, "");

        // Step 2: Verify 'fullName' getter is in suggestions
        assertThat(result.completions()).isNotEmpty();
        boolean hasFullName = result.completions().stream()
                .anyMatch(f -> f.name().equals("fullName") && f.kind() == FieldInfo.FieldKind.GETTER);
        assertThat(hasFullName)
                .withFailMessage("Tool should suggest 'fullName' getter method")
                .isTrue();

        // Step 3: Verify the path 'fullName' works in MapStruct
        // Using mapper: @Mapping(target = "fullName", source = "fullName")
        var dto = mapper.mapGetterMethod(testPerson);
        assertThat(dto.getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Tool returns empty suggestions for terminal types (String, primitives)")
    void testTerminalTypesReturnEmpty() {
        // After 'firstName' which is String, should have no completions
        CompletionResult result = pathNavigator.navigate(Person.class, "firstName.");

        assertThat(result.completions())
                .withFailMessage("Tool should return empty suggestions after terminal type (String)")
                .isEmpty();
    }

    @Test
    @DisplayName("Tool suggestions include both fields and getters")
    void testToolSuggestsBothFieldsAndGetters() {
        // Get suggestions for Person
        CompletionResult result = pathNavigator.navigate(Person.class, "");

        // Should have firstName (field)
        boolean hasFirstName = result.completions().stream()
                .anyMatch(f -> f.name().equals("firstName") && f.kind() == FieldInfo.FieldKind.FIELD);
        assertThat(hasFirstName).isTrue();

        // Should have fullName (getter method without field)
        boolean hasFullName = result.completions().stream()
                .anyMatch(f -> f.name().equals("fullName") && f.kind() == FieldInfo.FieldKind.GETTER);
        assertThat(hasFullName).isTrue();

        // Verify both work in MapStruct
        var dto = mapper.mapMixedAccess(testPerson);
        assertThat(dto.getFirstName()).isEqualTo("John");
        assertThat(dto.getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Tool handles prefix filtering correctly")
    void testPrefixFiltering() {
        // Get suggestions with prefix 'first'
        CompletionResult result = pathNavigator.navigate(Person.class, "first");

        // Should suggest fields starting with 'first'
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions().stream().anyMatch(f -> f.name().equals("firstName"))).isTrue();

        // Should NOT suggest 'age' or 'lastName'
        assertThat(result.completions().stream().anyMatch(f -> f.name().equals("age"))).isFalse();
        assertThat(result.completions().stream().anyMatch(f -> f.name().equals("lastName"))).isFalse();
    }

    @Test
    @DisplayName("Complete workflow: Build path from tool suggestions and verify in MapStruct")
    void testCompleteWorkflow() {
        // Simulate IDE autocomplete workflow:
        // User wants to map: orders -> first -> items -> first -> product -> price

        StringBuilder pathBuilder = new StringBuilder();

        // User types "orders"
        pathBuilder.append("orders");
        CompletionResult r1 = pathNavigator.navigate(Person.class, pathBuilder.toString());
        assertThat(r1.completions().stream().anyMatch(f -> f.name().equals("orders"))).isTrue();

        // User selects "orders" and types "."
        pathBuilder.append(".");
        CompletionResult r2 = pathNavigator.navigate(Person.class, pathBuilder.toString());
        List<String> suggestions = r2.completions().stream().map(FieldInfo::name).toList();
        assertThat(suggestions).contains("first", "last"); // Collection accessors

        // User selects "first" and types "."
        pathBuilder.append("first.");
        CompletionResult r3 = pathNavigator.navigate(Person.class, pathBuilder.toString());
        assertThat(r3.completions().stream().anyMatch(f -> f.name().equals("items"))).isTrue();

        // User selects "items" and types "."
        pathBuilder.append("items.");
        CompletionResult r4 = pathNavigator.navigate(Person.class, pathBuilder.toString());
        assertThat(r4.completions().stream().anyMatch(f -> f.name().equals("first"))).isTrue();

        // User selects "first" and types "."
        pathBuilder.append("first.");
        CompletionResult r5 = pathNavigator.navigate(Person.class, pathBuilder.toString());
        assertThat(r5.completions().stream().anyMatch(f -> f.name().equals("price"))).isTrue();

        // Final path: orders.first.items.first.price
        // Verify this path works in MapStruct
        var dto = mapper.mapMultiplePrimitives(testPerson);
        assertThat(dto.getPrice()).isEqualTo(1999.98);
    }

    @Test
    @DisplayName("Tool correctly handles 'last' collection accessor")
    void testCollectionLastAccessor() {
        // Get suggestions for 'orders.'
        CompletionResult result = pathNavigator.navigate(Person.class, "orders.");

        // Verify both 'first' and 'last' are suggested
        List<String> collectionAccessors = result.completions().stream()
                .map(FieldInfo::name)
                .filter(name -> name.equals("first") || name.equals("last"))
                .toList();

        assertThat(collectionAccessors)
                .withFailMessage("Tool should suggest both 'first' and 'last' collection accessors")
                .containsExactlyInAnyOrder("first", "last");

        // Verify 'orders.last.orderId' works in MapStruct
        var dto = mapper.mapCollectionLast(testPerson);
        assertThat(dto.getLastOrderId()).isEqualTo("ORD-123");
    }

    @Test
    @DisplayName("All paths used in TestMapper are valid according to tool")
    void testAllMapperPathsAreValidatedByTool() {
        // This test validates that all paths used in TestMapper would be suggested by the tool

        // Test: address.city
        CompletionResult addressDot = pathNavigator.navigate(Person.class, "address.");
        assertThat(addressDot.completions().stream().anyMatch(f -> f.name().equals("city"))).isTrue();

        // Test: address.state
        assertThat(addressDot.completions().stream().anyMatch(f -> f.name().equals("state"))).isTrue();

        // Test: address.zipCode
        assertThat(addressDot.completions().stream().anyMatch(f -> f.name().equals("zipCode"))).isTrue();

        // Test: address.country.name
        CompletionResult countryDot = pathNavigator.navigate(Person.class, "address.country.");
        assertThat(countryDot.completions().stream().anyMatch(f -> f.name().equals("name"))).isTrue();

        // Test: orders.first.customer.firstName
        CompletionResult customerDot = pathNavigator.navigate(Person.class, "orders.first.customer.");
        assertThat(customerDot.completions().stream().anyMatch(f -> f.name().equals("firstName"))).isTrue();

        // Verify all these paths actually work in MapStruct
        assertThat(mapper.mapComplexNested(testPerson).getCity()).isEqualTo("Springfield");
        assertThat(mapper.mapCompletePerson(testPerson).getCountry()).isEqualTo("United States");
    }
}
