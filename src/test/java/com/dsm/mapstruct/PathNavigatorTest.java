package com.dsm.mapstruct;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import com.dsm.mapstruct.model.CompletionResult;
import com.dsm.mapstruct.testdata.TestClasses.Company;
import com.dsm.mapstruct.testdata.TestClasses.Order;
import com.dsm.mapstruct.testdata.TestClasses.Person;
import com.dsm.mapstruct.testdata.TestClasses.PersonRecord;
import com.dsm.mapstruct.testdata.TestClasses.AddressRecord;
import com.dsm.mapstruct.testdata.TestClasses.OrderRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PathNavigatorTest {

    PathNavigator navigator = new PathNavigator();

    @Test
    void testNavigateToRootClass() {
        CompletionResult result = navigator.navigate(Person.class, "");

        assertThat(result.className()).isEqualTo(Person.class.getName());
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age", "address");
    }

    @Test
    void testNavigateToNestedField() {
        CompletionResult result = navigator.navigate(Person.class, "address.");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("street", "city", "state", "zipCode", "country");
    }

    @Test
    void testNavigateWithPartialPrefix() {
        CompletionResult result = navigator.navigate(Person.class, "address.st");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("street", "state")
                .doesNotContain("city", "zipCode");
    }

    @Test
    void testNavigateTwoLevelsDeep() {
        CompletionResult result = navigator.navigate(Person.class, "address.country.");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "code");
    }

    @Test
    void testNavigateWithGetter() {
        // Test navigating using actual getter method name
        // getCity() returns String, which is a terminal type
        CompletionResult result = navigator.navigate(Person.class, "address.getCity().");

        // String is a terminal type - should return empty completions
        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateThroughCollection() {
        // MapStruct uses property syntax: orders.first (not orders.getFirst())
        CompletionResult result = navigator.navigate(Person.class, "orders.first.");

        // After accessing first element of List<Order>, we should get Order fields
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("orderId", "items", "customer");
    }

    @Test
    void testNavigateThroughNestedCollection() {
        CompletionResult result = navigator.navigate(Order.class, "items.first.product.");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "sku", "price");
    }

    @Test
    void testNavigateWithCollectionAndPrefix() {
        CompletionResult result = navigator.navigate(Order.class, "items.first.product.n");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name")
                .doesNotContain("sku", "price");
    }

    @Test
    void testNavigateThroughArray() {
        // Arrays also support .first syntax in MapStruct
        CompletionResult result = navigator.navigate(Company.class, "employees.first.");

        // After navigating through Person[] array
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "address");
    }

    @Test
    void testNavigateInvalidPath() {
        CompletionResult result = navigator.navigate(Person.class, "nonExistentField.");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateWithMultipleCollections() {
        CompletionResult result = navigator.navigate(Company.class, "departments.first.members.first.");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName");
    }

    @Test
    void testNavigateFieldNames() {
        CompletionResult result = navigator.navigate(Person.class, "");

        // Both field and transformed getter should be present
        // Getter names are transformed: getFirstName() -> firstName
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "fullName"); // fullName only exists as getter
    }

    @Test
    void testPrefixMatchingCaseInsensitive() {
        CompletionResult result = navigator.navigate(Person.class, "First");

        assertThat(result.completions()).extracting("name")
                .contains("firstName")
                .doesNotContain("lastName", "age");
    }

    // ===== Java Record Tests =====

    @Test
    void testNavigateToRootRecord() {
        CompletionResult result = navigator.navigate(PersonRecord.class, "");

        assertThat(result.className()).isEqualTo(PersonRecord.class.getName());
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName", "lastName", "age");
    }

    @Test
    void testNavigateToNestedRecordField() {
        CompletionResult result = navigator.navigate(AddressRecord.class, "country.");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "code");
    }

    @Test
    void testNavigateRecordWithPartialPrefix() {
        CompletionResult result = navigator.navigate(PersonRecord.class, "first");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("firstName")
                .doesNotContain("lastName", "age");
    }

    @Test
    void testNavigateRecordTwoLevelsDeep() {
        CompletionResult result = navigator.navigate(AddressRecord.class, "country.name");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name");
    }

    @Test
    void testNavigateThroughRecordCollection() {
        CompletionResult result = navigator.navigate(OrderRecord.class, "items.first.");

        // After accessing first element of List<OrderItemRecord>, we should get OrderItemRecord fields
        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("product", "quantity", "price");
    }

    @Test
    void testNavigateThroughNestedRecordCollection() {
        CompletionResult result = navigator.navigate(OrderRecord.class, "items.first.product.");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name", "sku", "price");
    }

    @Test
    void testNavigateRecordWithCollectionAndPrefix() {
        CompletionResult result = navigator.navigate(OrderRecord.class, "items.first.product.n");

        assertThat(result.completions()).isNotEmpty();
        assertThat(result.completions()).extracting("name")
                .contains("name")
                .doesNotContain("sku", "price");
    }

    @Test
    void testNavigateRecordInvalidPath() {
        CompletionResult result = navigator.navigate(PersonRecord.class, "nonExistentField.");

        assertThat(result.completions()).isEmpty();
    }

    // ===== Terminal Type Tests =====

    @Test
    void testNavigateToStringField() {
        // Navigate to a String field - should return empty
        CompletionResult result = navigator.navigate(Person.class, "firstName.");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateToIntField() {
        // Navigate to an int field - should return empty
        CompletionResult result = navigator.navigate(Person.class, "age.");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateToNestedStringField() {
        // Navigate to nested String field - should return empty
        CompletionResult result = navigator.navigate(Person.class, "address.city.");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testRootClassIsString() {
        // Root class itself is String - should return empty
        CompletionResult result = navigator.navigate(String.class, "");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testRootClassIsInteger() {
        // Root class itself is Integer - should return empty
        CompletionResult result = navigator.navigate(Integer.class, "");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testRootClassIsPrimitive() {
        // Root class itself is primitive int - should return empty
        CompletionResult result = navigator.navigate(int.class, "");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateToDoubleField() {
        // Navigate to double field in OrderItem - should return empty
        CompletionResult result = navigator.navigate(Order.class, "items.first.price.");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateRecordToStringField() {
        // Navigate to String field in record - should return empty
        CompletionResult result = navigator.navigate(PersonRecord.class, "firstName.");

        assertThat(result.completions()).isEmpty();
    }

    @Test
    void testNavigateRecordToIntField() {
        // Navigate to int field in record - should return empty
        CompletionResult result = navigator.navigate(PersonRecord.class, "age.");

        assertThat(result.completions()).isEmpty();
    }
}
