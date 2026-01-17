package com.dsm.mapstruct.testdata;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Test data classes for unit testing.
 */
public class TestClasses {

    /**
     * Simple person class with fields and getters.
     */
    @Value
    @Builder(toBuilder = true)
    public static class Person {
        public String firstName;
        public String lastName;
        public int age;
        public Address address;
        public List<Order> orders;

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public int getAge() {
            return age;
        }

        public Address getAddress() {
            return address;
        }

        public List<Order> getOrders() {
            return orders;
        }

        public String getFullName() {
            return firstName + " " + lastName;
        }
    }

    /**
     * Address class with nested fields.
     */
    @Value
    @Builder
    public static class Address {
        public String street;
        public String city;
        public String state;
        public String zipCode;
        public Country country;

        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        public String getZipCode() {
            return zipCode;
        }

        public Country getCountry() {
            return country;
        }
    }

    /**
     * Country class.
     */
    @Value
    @Builder
    public static class Country {
        public String name;
        public String code;

        public String getName() {
            return name;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Order class with items collection.
     */
    @Value
    @Builder
    public static class Order {
        public String orderId;
        public List<OrderItem> items;
        public Person customer;

        public String getOrderId() {
            return orderId;
        }

        public List<OrderItem> getItems() {
            return items;
        }

        public Person getCustomer() {
            return customer;
        }
    }

    /**
     * Order item class.
     */
    @Value
    @Builder
    public static class OrderItem {
        public Product product;
        public int quantity;
        public double price;

        public Product getProduct() {
            return product;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getPrice() {
            return price;
        }
    }

    /**
     * Product class.
     */
    @Value
    @Builder
    public static class Product {
        public String name;
        public String sku;
        public double price;

        public String getName() {
            return name;
        }

        public String getSku() {
            return sku;
        }

        public double getPrice() {
            return price;
        }
    }

    /**
     * Company class with array of employees.
     */
    @Value
    @Builder
    public static class Company {
        public String name;
        public Person[] employees;
        public List<Department> departments;

        public String getName() {
            return name;
        }

        public Person[] getEmployees() {
            return employees;
        }

        public List<Department> getDepartments() {
            return departments;
        }
    }

    /**
     * Department class.
     */
    @Value
    @Builder
    public static class Department {
        public String name;
        public Person head;
        public List<Person> members;

        public String getName() {
            return name;
        }

        public Person getHead() {
            return head;
        }

        public List<Person> getMembers() {
            return members;
        }
    }

    /**
     * Simple Java record with basic fields.
     */
    @Builder
    public record PersonRecord(String firstName, String lastName, int age) {
    }

    /**
     * Java record with nested record.
     */
    @Builder
    public record AddressRecord(String street, String city, String zipCode, CountryRecord country) {
    }

    /**
     * Simple country record.
     */
    @Builder
    public record CountryRecord(String name, String code) {
    }

    /**
     * Java record with collection.
     */
    @Builder
    public record OrderRecord(String orderId, List<OrderItemRecord> items, PersonRecord customer) {
    }

    /**
     * Order item record.
     */
    @Builder
    public record OrderItemRecord(ProductRecord product, int quantity, double price) {
    }

    /**
     * Product record.
     */
    @Builder
    public record ProductRecord(String name, String sku, double price) {
    }

    /**
     * Order status enum for testing @ValueMapping.
     */
    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }

    /**
     * Order state enum for testing @ValueMapping.
     */
    public enum OrderState {
        NEW,
        PROCESSING,
        IN_TRANSIT,
        COMPLETED,
        REJECTED
    }

    /**
     * Simple POJO with JavaBean-style setters for testing setter detection.
     */
    public static class PersonPojo {
        private String name;
        private int age;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    /**
     * Fluent builder-style class for testing builder setter detection.
     */
    public static class FluentBuilder {
        private String title;
        private String description;
        private int priority;

        public FluentBuilder title(String title) {
            this.title = title;
            return this;
        }

        public FluentBuilder description(String description) {
            this.description = description;
            return this;
        }

        public FluentBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public FluentBuilder build() {
            return this;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getPriority() {
            return priority;
        }
    }
}
