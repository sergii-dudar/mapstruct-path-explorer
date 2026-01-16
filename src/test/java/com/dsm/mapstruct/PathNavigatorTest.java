package com.dsm.mapstruct;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import com.dsm.mapstruct.core.usecase.helper.PathNavigator;
import com.dsm.mapstruct.core.model.CompletionResult;
import com.dsm.mapstruct.testdata.TestClasses.Company;
import com.dsm.mapstruct.testdata.TestClasses.Order;
import com.dsm.mapstruct.testdata.TestClasses.Person;
import com.dsm.mapstruct.testdata.TestClasses.PersonRecord;
import com.dsm.mapstruct.testdata.TestClasses.AddressRecord;
import com.dsm.mapstruct.testdata.TestClasses.OrderRecord;
import com.dsm.mapstruct.core.model.SourceParameter;
import com.dsm.mapstruct.core.model.FieldInfo.FieldKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PathNavigatorTest {

    PathNavigator navigator = new PathNavigator();

    @Test
    void testNavigateToRootClass() {
        CompletionResult result = navigator.navigate(Person.class, "", false);

        assertThat(result.className()).isEqualTo(Person.class.getName());
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age", "address");
    }

    @Test
    void testNavigateToNestedField() {
        CompletionResult result = navigator.navigate(Person.class, "address.", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("street", "city", "state", "zipCode", "country");
    }

    @Test
    void testNavigateWithPartialPrefix() {
        CompletionResult result = navigator.navigate(Person.class, "address.st", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("street", "state")
                .doesNotContain("city", "zipCode");
    }

    @Test
    void testNavigateTwoLevelsDeep() {
        CompletionResult result = navigator.navigate(Person.class, "address.country.", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "code");
    }

    @Test
    void testNavigateWithGetter() {
        // Test navigating using actual getter method name
        // getCity() returns String, which is a terminal type
        CompletionResult result = navigator.navigate(Person.class, "address.getCity().", false);

        // String is a terminal type - should return empty completions
        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateThroughCollection() {
        // MapStruct uses property syntax: orders.first (not orders.getFirst())
        CompletionResult result = navigator.navigate(Person.class, "orders.first.", false);

        // After accessing first element of List<Order>, we should get Order fields
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("orderId", "items", "customer");
    }

    @Test
    void testNavigateThroughNestedCollection() {
        CompletionResult result = navigator.navigate(Order.class, "items.first.product.", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "sku", "price");
    }

    @Test
    void testNavigateWithCollectionAndPrefix() {
        CompletionResult result = navigator.navigate(Order.class, "items.first.product.n", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name")
                .doesNotContain("sku", "price");
    }

    @Test
    void testNavigateThroughArray() {
        // Arrays also support .first syntax in MapStruct
        CompletionResult result = navigator.navigate(Company.class, "employees.first.", false);

        // After navigating through Person[] array
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "address");
    }

    @Test
    void testNavigateInvalidPath() {
        CompletionResult result = navigator.navigate(Person.class, "nonExistentField.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateWithMultipleCollections() {
        CompletionResult result = navigator.navigate(Company.class, "departments.first.members.first.", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName");
    }

    @Test
    void testNavigateFieldNames() {
        CompletionResult result = navigator.navigate(Person.class, "", false);

        // Both field and transformed getter should be present
        // Getter names are transformed: getFirstName() -> firstName
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "fullName"); // fullName only exists as getter
    }

    @Test
    void testPrefixMatchingCaseInsensitive() {
        CompletionResult result = navigator.navigate(Person.class, "First", false);

        assertThat(result.completions()).extracting("name")
                .contains("firstName")
                .doesNotContain("lastName", "age");
    }

    // ===== Java Record Tests =====

    @Test
    void testNavigateToRootRecord() {
        CompletionResult result = navigator.navigate(PersonRecord.class, "", false);

        assertThat(result.className()).isEqualTo(PersonRecord.class.getName());
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age");
    }

    @Test
    void testNavigateToNestedRecordField() {
        CompletionResult result = navigator.navigate(AddressRecord.class, "country.", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "code");
    }

    @Test
    void testNavigateRecordWithPartialPrefix() {
        CompletionResult result = navigator.navigate(PersonRecord.class, "first", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName")
                .doesNotContain("lastName", "age");
    }

    @Test
    void testNavigateRecordTwoLevelsDeep() {
        CompletionResult result = navigator.navigate(AddressRecord.class, "country.name", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name");
    }

    @Test
    void testNavigateThroughRecordCollection() {
        CompletionResult result = navigator.navigate(OrderRecord.class, "items.first.", false);

        // After accessing first element of List<OrderItemRecord>, we should get OrderItemRecord fields
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("product", "quantity", "price");
    }

    @Test
    void testNavigateThroughNestedRecordCollection() {
        CompletionResult result = navigator.navigate(OrderRecord.class, "items.first.product.", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "sku", "price");
    }

    @Test
    void testNavigateRecordWithCollectionAndPrefix() {
        CompletionResult result = navigator.navigate(OrderRecord.class, "items.first.product.n", false);

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name")
                .doesNotContain("sku", "price");
    }

    @Test
    void testNavigateRecordInvalidPath() {
        CompletionResult result = navigator.navigate(PersonRecord.class, "nonExistentField.", false);

        assertThat(result.completions()).isEmpty();
    }

    // ===== Terminal Type Tests =====

    @Test
    void testNavigateToStringField() {
        // Navigate to a String field - should return empty
        CompletionResult result = navigator.navigate(Person.class, "firstName.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateToIntField() {
        // Navigate to an int field - should return empty
        CompletionResult result = navigator.navigate(Person.class, "age.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateToNestedStringField() {
        // Navigate to nested String field - should return empty
        CompletionResult result = navigator.navigate(Person.class, "address.city.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testRootClassIsString() {
        // Root class itself is String - should return empty
        CompletionResult result = navigator.navigate(String.class, "", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testRootClassIsInteger() {
        // Root class itself is Integer - should return empty
        CompletionResult result = navigator.navigate(Integer.class, "", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testRootClassIsPrimitive() {
        // Root class itself is primitive int - should return empty
        CompletionResult result = navigator.navigate(int.class, "", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateToDoubleField() {
        // Navigate to double field in OrderItem - should return empty
        CompletionResult result = navigator.navigate(Order.class, "items.first.price.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateRecordToStringField() {
        // Navigate to String field in record - should return empty
        CompletionResult result = navigator.navigate(PersonRecord.class, "firstName.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateRecordToIntField() {
        // Navigate to int field in record - should return empty
        CompletionResult result = navigator.navigate(PersonRecord.class, "age.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testEnumConstants_OrderStatus() {
        // Test @ValueMapping - should return enum constants
        CompletionResult result = navigator.navigate(
            com.dsm.mapstruct.testdata.TestClasses.OrderStatus.class,
            "",
            true  // isEnum = true for @ValueMapping
        );

        assertThat(result.className()).isEqualTo("com.dsm.mapstruct.testdata.TestClasses$OrderStatus");
        assertThat(result.completions()).hasSize(5);
        assertThat(result.completions()).extracting("name")
                .containsExactlyInAnyOrder("PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED");
        assertThat(result.completions()).extracting("kind")
                .containsOnly(com.dsm.mapstruct.core.model.FieldInfo.FieldKind.FIELD);
    }

    @Test
    void testEnumConstants_OrderState() {
        // Test @ValueMapping with different enum - should return its constants
        CompletionResult result = navigator.navigate(
            com.dsm.mapstruct.testdata.TestClasses.OrderState.class,
            "",
            true  // isEnum = true for @ValueMapping
        );

        assertThat(result.className()).isEqualTo("com.dsm.mapstruct.testdata.TestClasses$OrderState");
        assertThat(result.completions()).hasSize(5);
        assertThat(result.completions()).extracting("name")
                .containsExactlyInAnyOrder("NEW", "PROCESSING", "IN_TRANSIT", "COMPLETED", "REJECTED");
        assertThat(result.completions()).extracting("kind")
                .containsOnly(com.dsm.mapstruct.core.model.FieldInfo.FieldKind.FIELD);
    }

    // ===== Multi-Parameter Mapper Support Tests =====

    @Test
    void testNavigateFromSources_EmptyPath_MultipleParams_ReturnsParameterNames() {
        // Test: CompletePersonDTO map(Person person, Order order, String customFullName)
        // Empty path with MULTIPLE parameters should return all parameter names
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order"),
            new SourceParameter("customFullName", "java.lang.String")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "", false);

        assertThat(result.completions()).hasSize(3);
        assertThat(result.completions()).extracting("name")
                .containsExactlyInAnyOrder("person", "order", "customFullName");
        assertThat(result.completions()).extracting("kind")
                .containsOnly(FieldKind.PARAMETER);
    }

    @Test
    void testNavigateFromSources_EmptyPath_SingleParam_ReturnsFields() {
        // Test: CompletePersonDTO map(Person person)
        // Empty path with SINGLE parameter should navigate directly to fields (not show parameter name)
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "", false);

        // Should show Person fields directly, not the parameter name
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age", "address");
        assertThat(result.completions()).extracting("kind")
                .doesNotContain(FieldKind.PARAMETER);
    }

    @Test
    void testNavigateFromSources_PartialParameterName_FiltersMatching() {
        // Test prefix matching on parameter names
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order"),
            new SourceParameter("customFullName", "java.lang.String")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "per", false);

        assertThat(result.completions()).hasSize(1);
        assertThat(result.completions()).extracting("name")
                .containsExactly("person");
        assertThat(result.completions()).extracting("kind")
                .containsOnly(FieldKind.PARAMETER);
    }

    @Test
    void testNavigateFromSources_PartialParameterName_CaseInsensitive() {
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "ORD", false);

        assertThat(result.completions()).hasSize(1);
        assertThat(result.completions()).extracting("name")
                .containsExactly("order");
    }

    @Test
    void testNavigateFromSources_FullParameterName_NavigatesToFields() {
        // Test: person. should show Person's fields
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "person.", false);

        assertThat(result.className()).isEqualTo("com.dsm.mapstruct.testdata.TestClasses$Person");
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age", "address");
        assertThat(result.completions()).extracting("kind")
                .doesNotContain(FieldKind.PARAMETER);
    }

    @Test
    void testNavigateFromSources_NestedPath_FromSpecificParameter() {
        // Test: person.address.street should navigate into Person -> Address -> street
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "person.address.", false);

        assertThat(result.completions()).extracting("name")
                .contains("street", "city", "state", "zipCode", "country");
    }

    @Test
    void testNavigateFromSources_MultipleParameters_DifferentPaths() {
        // Test navigation from second parameter: order.customer.
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "order.customer.", false);

        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age", "address");
    }

    @Test
    void testNavigateFromSources_DeepPath_ThroughCollections() {
        // Test: person.orders.first.items.first.product.
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(
            sources,
            "person.orders.first.items.first.product.",
            false
        );

        assertThat(result.completions()).extracting("name")
                .contains("name", "sku", "price");
    }

    @Test
    void testNavigateFromSources_InvalidParameterName_ReturnsEmpty() {
        // Test with non-existent parameter name
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "invalid.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateFromSources_SingleParameter_WorksAsExpected() {
        // Edge case: single parameter (backward compatibility)
        // For single-parameter sources, empty path should navigate directly to fields
        // This handles both target attributes and single-parameter source mappers
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person")
        );

        // Empty path should navigate directly into Person fields (NOT return parameter name)
        CompletionResult result1 = navigator.navigateFromSources(sources, "", false);
        assertThat(result1.completions()).extracting("name")
                .contains("firstName", "lastName", "age", "address");
        assertThat(result1.completions()).extracting("kind")
                .doesNotContain(FieldKind.PARAMETER); // No parameter names shown

        // With path should navigate into type
        CompletionResult result2 = navigator.navigateFromSources(sources, "person.", false);
        assertThat(result2.completions()).extracting("name")
                .contains("firstName", "lastName", "age");
    }

    @Test
    void testNavigateFromSources_TerminalType_ReturnsEmpty() {
        // Test: customFullName. (String is terminal type)
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("customFullName", "java.lang.String")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "customFullName.", false);

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateFromSources_WithPrefixFilter_FromParameter() {
        // Test: person.address.st should filter to street and state
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "person.address.st", false);

        assertThat(result.completions()).extracting("name")
                .contains("street", "state")
                .doesNotContain("city", "zipCode");
    }

    @Test
    void testNavigateFromSources_MixedParameterTypes() {
        // Test with String, Person, Order (real multi-param mapper scenario)
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order"),
            new SourceParameter("customFullName", "java.lang.String")
        );

        // Test navigation from each parameter
        CompletionResult personResult = navigator.navigateFromSources(sources, "person.age", false);
        assertThat(personResult.completions()).extracting("name").contains("age");

        CompletionResult orderResult = navigator.navigateFromSources(sources, "order.orderId", false);
        assertThat(orderResult.completions()).extracting("name").contains("orderId");

        CompletionResult stringResult = navigator.navigateFromSources(sources, "customFullName.", false);
        assertThat(stringResult.completions()).isEmpty(); // String is terminal
    }

    @Test
    void testNavigateFromSources_OrderCustomerAddressPath() {
        // Real-world scenario from TestMapper.java line 144:
        // @Mapping(target = "city", source = "order.customer.address.city")
        List<SourceParameter> sources = List.of(
            new SourceParameter("person", "com.dsm.mapstruct.testdata.TestClasses$Person"),
            new SourceParameter("order", "com.dsm.mapstruct.testdata.TestClasses$Order"),
            new SourceParameter("customFullName", "java.lang.String")
        );

        CompletionResult result = navigator.navigateFromSources(
            sources,
            "order.customer.address.",
            false
        );

        assertThat(result.completions()).extracting("name")
                .contains("street", "city", "state", "zipCode");
    }

    @Test
    void testNavigateFromSources_BackwardCompatibility_SingleParam_DirectPath() {
        // BACKWARD COMPATIBILITY TEST
        // For single-parameter mappers where path doesn't include parameter name
        // This handles: CompletePersonDTO map(Person person)
        // When user types "address." but parameter is named "param0" (no debug info)
        List<SourceParameter> sources = List.of(
            new SourceParameter("param0", "com.dsm.mapstruct.testdata.TestClasses$Person")
        );

        // Path "address." doesn't start with "param0", but for single-param we should
        // navigate directly from Person type
        CompletionResult result = navigator.navigateFromSources(sources, "address.", false);

        assertThat(result.completions()).extracting("name")
                .contains("street", "city", "state", "zipCode", "country");
    }

    @Test
    void testNavigateFromSources_BackwardCompatibility_SingleParam_NestedPath() {
        // Test deeper navigation for single-param backward compatibility
        List<SourceParameter> sources = List.of(
            new SourceParameter("param0", "com.dsm.mapstruct.testdata.TestClasses$Person")
        );

        CompletionResult result = navigator.navigateFromSources(
            sources,
            "address.country.",
            false
        );

        assertThat(result.completions()).extracting("name")
                .contains("name", "code");
    }

    @Test
    void testNavigateFromSources_BackwardCompatibility_SingleParam_WithPrefix() {
        // Test prefix filtering in backward compatibility mode
        List<SourceParameter> sources = List.of(
            new SourceParameter("param0", "com.dsm.mapstruct.testdata.TestClasses$Person")
        );

        CompletionResult result = navigator.navigateFromSources(sources, "address.st", false);

        assertThat(result.completions()).extracting("name")
                .contains("street", "state")
                .doesNotContain("city", "zipCode");
    }
}
