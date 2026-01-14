package com.dsm.mapstruct;

import com.dsm.mapstruct.core.model.FieldInfo;
import com.dsm.mapstruct.core.usecase.helper.ReflectionAnalyzer;
import com.dsm.mapstruct.testdata.TestClasses.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ReflectionAnalyzerTest {

    ReflectionAnalyzer analyzer = new ReflectionAnalyzer();

    @Test
    void testGetAllFields() {
        List<FieldInfo> fields = analyzer.getAllFields(Person.class);

        assertThat(fields).isNotEmpty();
        assertThat(fields).extracting(FieldInfo::name)
                .contains("firstName", "lastName", "age", "address", "orders");
        assertThat(fields).allMatch(f -> f.kind() == FieldInfo.FieldKind.FIELD);
    }

    @Test
    void testGetAllGetters() {
        List<FieldInfo> getters = analyzer.getAllGetters(Person.class);

        assertThat(getters).isNotEmpty();
        // Getter names are transformed to MapStruct property format (getFirstName -> firstName)
        assertThat(getters).extracting(FieldInfo::name)
                .contains("firstName", "lastName", "age", "address", "orders", "fullName");
        assertThat(getters).allMatch(f -> f.kind() == FieldInfo.FieldKind.GETTER);
    }

    @Test
    void testGetAllFieldsAndGetters() {
        List<FieldInfo> all = analyzer.getAllFieldsAndGetters(Person.class);

        assertThat(all).hasSizeGreaterThanOrEqualTo(6); // At least 5 fields + 6 getters
        // Both fields and transformed getter names should be present
        assertThat(all).extracting(FieldInfo::name)
                .contains("firstName", "address"); // Both appear as field and as transformed getter
    }

    @Test
    void testGetFieldOrGetterType_Field() {
        Class<?> type = analyzer.getFieldOrGetterType(Person.class, "firstName");

        assertThat(type).isEqualTo(String.class);
    }

    @Test
    void testGetFieldOrGetterType_IntField() {
        Class<?> type = analyzer.getFieldOrGetterType(Person.class, "age");

        assertThat(type).isEqualTo(int.class);
    }

    @Test
    void testGetFieldOrGetterType_ComplexType() {
        Class<?> type = analyzer.getFieldOrGetterType(Person.class, "address");

        assertThat(type).isEqualTo(Address.class);
    }

    @Test
    void testGetFieldOrGetterType_Getter() {
        // Should work with both actual method name and transformed property name
        Class<?> type1 = analyzer.getFieldOrGetterType(Person.class, "getFirstName");
        assertThat(type1).isEqualTo(String.class);

        // Also test with MapStruct property format (though this will match field first)
        Class<?> type2 = analyzer.getFieldOrGetterType(Person.class, "firstName");
        assertThat(type2).isEqualTo(String.class);
    }

    @Test
    void testGetFieldOrGetterType_NonExistent() {
        Class<?> type = analyzer.getFieldOrGetterType(Person.class, "nonExistent");

        assertThat(type).isNull();
    }

    @Test
    void testGetMethodReturnType() {
        Class<?> type = analyzer.getMethodReturnType(Person.class, "getFirstName");

        assertThat(type).isEqualTo(String.class);
    }

    @Test
    void testGetMethodReturnType_NonExistent() {
        Class<?> type = analyzer.getMethodReturnType(Person.class, "nonExistentMethod");

        assertThat(type).isNull();
    }

    @Test
    void testAddressFields() {
        List<FieldInfo> fields = analyzer.getAllFields(Address.class);

        assertThat(fields).extracting(FieldInfo::name)
                .contains("street", "city", "state", "zipCode", "country");
    }

    @Test
    void testFieldTypeNames() {
        List<FieldInfo> fields = analyzer.getAllFieldsAndGetters(Address.class);

        FieldInfo streetField = fields.stream()
                .filter(f -> f.name().equals("street"))
                .findFirst()
                .orElseThrow();

        assertThat(streetField.type()).isEqualTo("String");
    }

    // ===== Java Record Tests =====

    @Test
    void testRecordGetters() {
        List<FieldInfo> getters = analyzer.getAllGetters(PersonRecord.class);

        assertThat(getters).isNotEmpty();
        // Record component accessor methods should be returned with their component names
        assertThat(getters).extracting(FieldInfo::name)
                .contains("firstName", "lastName", "age");
        assertThat(getters).allMatch(f -> f.kind() == FieldInfo.FieldKind.GETTER);
    }

    @Test
    void testRecordGetterTypes() {
        List<FieldInfo> getters = analyzer.getAllGetters(PersonRecord.class);

        FieldInfo firstNameGetter = getters.stream()
                .filter(f -> f.name().equals("firstName"))
                .findFirst()
                .orElseThrow();

        assertThat(firstNameGetter.type()).isEqualTo("String");

        FieldInfo ageGetter = getters.stream()
                .filter(f -> f.name().equals("age"))
                .findFirst()
                .orElseThrow();

        assertThat(ageGetter.type()).isEqualTo("int");
    }

    @Test
    void testRecordGetFieldOrGetterType() {
        Class<?> firstNameType = analyzer.getFieldOrGetterType(PersonRecord.class, "firstName");
        assertThat(firstNameType).isEqualTo(String.class);

        Class<?> ageType = analyzer.getFieldOrGetterType(PersonRecord.class, "age");
        assertThat(ageType).isEqualTo(int.class);
    }

    @Test
    void testRecordWithNestedRecord() {
        List<FieldInfo> getters = analyzer.getAllGetters(AddressRecord.class);

        assertThat(getters).extracting(FieldInfo::name)
                .contains("street", "city", "zipCode", "country");

        FieldInfo countryGetter = getters.stream()
                .filter(f -> f.name().equals("country"))
                .findFirst()
                .orElseThrow();

        assertThat(countryGetter.type()).isEqualTo("CountryRecord");
    }

    @Test
    void testRecordNavigateToNestedRecord() {
        Class<?> countryType = analyzer.getFieldOrGetterType(AddressRecord.class, "country");
        assertThat(countryType).isEqualTo(CountryRecord.class);

        // Now get fields from the nested record
        List<FieldInfo> countryGetters = analyzer.getAllGetters(countryType);
        assertThat(countryGetters).extracting(FieldInfo::name)
                .contains("name", "code");
    }

    @Test
    void testRecordWithCollection() {
        List<FieldInfo> getters = analyzer.getAllGetters(OrderRecord.class);

        assertThat(getters).extracting(FieldInfo::name)
                .contains("orderId", "items", "customer");

        FieldInfo itemsGetter = getters.stream()
                .filter(f -> f.name().equals("items"))
                .findFirst()
                .orElseThrow();

        assertThat(itemsGetter.type()).isEqualTo("List");
    }

    @Test
    void testRecordFields() {
        // Records don't have explicit fields, only the implicit private final fields
        // which are not public, so getAllFields should return empty
        List<FieldInfo> fields = analyzer.getAllFields(PersonRecord.class);

        assertThat(fields).isEmpty();
    }

    @Test
    void testRecordAllFieldsAndGetters() {
        List<FieldInfo> all = analyzer.getAllFieldsAndGetters(PersonRecord.class);

        // Should contain only getters (record component accessors)
        assertThat(all).isNotEmpty();
        assertThat(all).extracting(FieldInfo::name)
                .contains("firstName", "lastName", "age");
        assertThat(all).allMatch(f -> f.kind() == FieldInfo.FieldKind.GETTER);
    }
}
