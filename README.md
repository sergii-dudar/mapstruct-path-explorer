# MapStruct Path Completion Tool

A command-line tool (with IPC support) that provides MapStruct-style path completion using Java reflection. This tool helps to explore nested object paths, navigate through collections, and discover available fields and getters at any point in your object graph. Working as cmd one shoot tool or by IPC (Unix Domain Socket) to long running clients (like NeoVim).

## Why

I love to work with java code as professional java software engineer in NeoVim. As also hard user of intellij in previous, I wanted to have similar mapping helping ability as provided by [Intellij MapStruct Pligin](https://plugins.jetbrains.com/plugin/10036-mapstruct-support)
I have created this tool to use in pair with my neovim config with [blink.cmp](https://github.com/saghen/blink.cmp) completion source.

# Note

This project is under early development state, and I don't know when it will be finished for wide using.

## Features

- Navigate through nested object paths (e.g., `field1.field2.field3`)
- Handle collections and their item accessors (e.g., `items.first`, `list.last`)
- Support for arrays and generic types
- Full support for Java records (accessor methods like `name()`, `age()`)
- **Setter detection** for JavaBean-style setters and builder patterns
- **@MappingTarget parameter detection** for void return type mappers
- **Multi-parameter mapper support** with parameter name completion
- **Smart field kind detection** - GETTER for source, SETTER for target
- Prefix matching for autocomplete suggestions
- JSON output format for easy integration
- Works with any Java class on the classpath
- **IPC support via Unix Domain Sockets for persistent sessions**
- **Be very lightweight**

## Requirements

- At least basic `java`, `jvm` and `jdk tools` knowledge
- Java 25 or later
- Maven 3.9.11+ (for building)
  NOTE: for now it's just my personal experiment, without attention to using by somebody else, because of that, I just using latest versions I need and what installed on my machines.

## Building

```bash
cd mapstruct-path-explorer
./mvnw clean package -U -DskipTests
```

This will create two JAR files in the `target/` directory:

- `mapstruct-path-explorer-*.jar` - Regular JAR
- `mapstruct-path-explorer.jar` - Executable (FAT) JAR with all dependencies
- for easy automatic installations [install_explorer script](./install_explorer.sh), it's installing\updating to `$HOME/tools/java-extensions/mapstruct`

## NeoVim integration by blink.cmp

Lua IPC client for neovim, right now it's just part of my nvim configuration, and it's also on very early draft state. it's working, but requiring some work testing, improving and stabilization. In my plans in the future - it's to create separated nvim plugin `blink-cmp-java-mapstruct` with automatic installing\updating server.

### Screenshots of real working in neovim:

![@Mapping](./docs/images/20260115-015340.png)
![@ValueMapping](./docs/images/20260115-015424.png)

## Usage

This tool is primarily designed for IPC integration with editors like NeoVim. See the **IPC Protocol** section below for the current API format.

## Supported Path Expressions

### Field Access

- `field1` - Access a field
- `field1.field2` - Navigate through nested fields
- `field1.field2.field3` - Multiple levels deep

### Method Calls

- `getFirst()` - Call a no-arg method
- `getAddress().getCity()` - Chain method calls

### Collection Navigation

MapStruct uses **property-style syntax** for collection accessors:

- `orders.first` - Access first element (not `orders.getFirst()`)
- `orders.last` - Access last element (not `orders.getLast()`)
- `items.empty` - Check if empty

**Important**: MapStruct does NOT support method call syntax like `getFirst()`. Always use property-style:

- ✅ Correct: `orders.first.customer.name`
- ❌ Wrong: `orders.getFirst().customer.name`

SequencedCollection<> implementations (like Lists) supporting `first` and `last` properties.

### Prefix Matching

- `address.str` - Shows all fields starting with "str" (case-insensitive)
- `address.` - Shows all fields (empty prefix)

## How It Works

1. **Path Parsing**: Parses the MapStruct path expression into segments
2. **Reflection Analysis**: Uses Java reflection to analyze class structure
3. **Type Resolution**: Resolves generic types in collections (e.g., `List<Person>` → `Person`)
4. **Navigation**: Follows the path through the object graph
5. **Completion**: Returns available fields/getters/setters at the final location
6. **Filtering**: Applies prefix matching if a partial segment is provided

## IPC Protocol (Unix Domain Socket)

For persistent sessions (like Neovim integration), the tool supports IPC communication via Unix Domain Sockets.

### Starting the IPC Server

```bash
java -jar mapstruct-path-explorer.jar /tmp/mapstruct-ipc.sock
```

### Protocol Format

The protocol uses JSON messages with the following structure:

#### Request Format

```json
{
  "id": "unique-request-id",
  "method": "explore_path",
  "params": {
    "sources": [
      { "name": "person", "type": "com.example.Person" },
      { "name": "order", "type": "com.example.Order" }
    ],
    "pathExpression": "person.address.",
    "isEnum": false
  }
}
```

**Parameters:**

- `sources` (array, required): List of source parameters
  - `name` (string): Parameter name (use `"$target"` for target attribute completion)
  - `type` (string): Fully qualified class name
- `pathExpression` (string, required): MapStruct path expression
- `isEnum` (boolean, required): `true` for @ValueMapping enum constants, `false` otherwise

#### Response Format

```json
{
  "id": "unique-request-id",
  "result": {
    "className": "com.example.Address",
    "simpleName": "Address",
    "packageName": "com.example",
    "path": "person.address.",
    "completions": [
      { "name": "street", "type": "String", "kind": "GETTER" },
      { "name": "city", "type": "String", "kind": "SETTER" }
    ]
  }
}
```

**Field Kinds:**

- `FIELD`: Public field access
- `GETTER`: Getter method (for source mappings)
- `SETTER`: Setter method or builder method (for target mappings)
- `PARAMETER`: Method parameter (for multi-source mappers)

#### Error Response

```json
{
  "id": "unique-request-id",
  "error": "Error message"
}
```

### Multi-Parameter Mapper Support

For multi-parameter mappers like:

```java
CompletePersonDTO map(Person person, Order order, String customName);
```

**Empty Path** - Returns parameter names:

```json
{
  "sources": [
    { "name": "person", "type": "com.example.Person" },
    { "name": "order", "type": "com.example.Order" },
    { "name": "customName", "type": "java.lang.String" }
  ],
  "pathExpression": "",
  "isEnum": false
}
```

Response:

```json
{
  "completions": [
    { "name": "person", "type": "com.example.Person", "kind": "PARAMETER" },
    { "name": "order", "type": "com.example.Order", "kind": "PARAMETER" },
    { "name": "customName", "type": "java.lang.String", "kind": "PARAMETER" }
  ]
}
```

**With Path** - Navigate from specific parameter:

```json
{
  "sources": [...],
  "pathExpression": "person.address.street",
  "isEnum": false
}
```

### Target Attribute Completion

For target attribute mappings, use the synthetic `"$target"` parameter name:

```json
{
  "sources": [{ "name": "$target", "type": "com.example.PersonDTO" }],
  "pathExpression": "",
  "isEnum": false
}
```

This will automatically:

1. Navigate directly into the target class fields
2. Convert all GETTER/FIELD kinds to SETTER kind
3. Include setter methods for mutable classes and builder patterns

### @MappingTarget Support

For methods with `@MappingTarget` and `void` return type:

```java
void mapPerson(@MappingTarget PersonDTO dto, Person person);
```

The tool automatically:

1. Detects the `@MappingTarget` parameter from bytecode
2. Uses the `@MappingTarget` parameter type as the target class
3. Excludes `@MappingTarget` parameters from source completions

### Setter Detection

The tool detects and includes setters in completions:

**JavaBean-style setters:**

```java
public void setName(String name) { ... }  // → "name" (SETTER)
```

**Builder-style setters:**

```java
public Builder name(String name) { return this; }  // → "name" (SETTER)
```

**Fluent setters:**

```java
public Person withName(String name) { return this; }  // → "withName" (SETTER)
```

## Architecture

```
MapStructPathTool (Main)
    │
    └─> PathNavigator
            ├─> PathParser
            │   └─> Parses path expressions
            │
            ├─> ReflectionAnalyzer
            │   └─> Extracts public fields and getters
            │
            ├─> CollectionTypeResolver
            │   └─> Resolves generic types
            │
            └─> NameMatcher
                └─> Filters by prefix
```

## Testing

Run the unit tests:

```bash
./mvnw test
```

The test suite includes:

- **PathParser tests** - Path expression parsing
- **ReflectionAnalyzer tests** - Class introspection (fields, getters, setters, Java records)
- **PathNavigator tests** - End-to-end navigation tests including:
  - Multi-parameter mapper support
  - Target completion with GETTER→SETTER conversion
  - Nested path navigation
  - Collection accessors
- **IpcServer tests** - IPC protocol tests including:
  - Multi-source API
  - JavaBean setter detection
  - Builder pattern setter detection
- **MapStructIntegration tests** - Real MapStruct mapper validation

**Test Coverage:** 150+ tests covering all major features

## Integration with IDEs

This tool can be integrated with IDE plugins to provide MapStruct path completion via the IPC protocol:

1. Editor plugin starts the IPC server on a Unix Domain Socket
2. Plugin sends `explore_path` requests with source types and path expressions
3. Server responds with JSON completion suggestions (fields, getters, setters, parameters)
4. Plugin displays suggestions to user with appropriate kind indicators

See the **IPC Protocol** section above for detailed request/response formats.

## Field Kind Reference

The tool returns different field kinds based on the context:

| Kind        | Description           | Example               | Usage                |
| ----------- | --------------------- | --------------------- | -------------------- |
| `FIELD`     | Public field          | `public String name;` | Source/Target        |
| `GETTER`    | Getter method         | `getName()` → `name`  | Source               |
| `SETTER`    | Setter/Builder method | `setName()` → `name`  | Target               |
| `PARAMETER` | Method parameter      | `map(Person person)`  | Source (multi-param) |

**Automatic Conversion for Target:**
When completing target attributes (using `"$target"` parameter name), the tool automatically converts:

- `FIELD` → `SETTER`
- `GETTER` → `SETTER`

This provides better UX by showing fields as "writable" when configuring target mappings, even for immutable classes.

## Limitations

- Only works with compiled classes on the classpath
- Requires Java 17+ for `getFirst()`/`getLast()` support on collections and record support
- Method parameters in paths are ignored (e.g., `get(0)` treats 0 as placeholder)
- Raw collection types return `Object` as item type
- Does not support complex generic type scenarios (e.g., nested generics)
- Only returns public members (fields, getters, setters) as MapStruct can only access public members
- Returns empty completions for terminal types (primitives, wrapper types like Integer, and String) as they have no useful MapStruct properties to navigate to
- @MappingTarget detection requires parameter annotations to be available in compiled bytecode (compile with `-parameters` flag or use debug info)

## Troubleshooting

### Class Not Found Error

**Problem**: `ClassNotFoundException` when the IPC server tries to analyze a class

**Solution**: Ensure your project classes are on the classpath when starting the IPC server. The server inherits the classpath from its launch environment. For editor integrations, configure the classpath to include your compiled classes and dependencies.

### Empty Completions

**Problem**: IPC server returns empty completions

**Possible causes**:

- Invalid path (field/method doesn't exist)
- Class doesn't have any accessible fields or getters
- Incorrect fully qualified class name in `sources[].type`
- Class not on the server's classpath

**Solution**:

- Check server logs for error messages
- Verify the class name is fully qualified (e.g., `com.example.User`, not `User`)
- Ensure the class is compiled and on the classpath
- Test with empty path first, then add segments incrementally

### Java Version Issues

**Problem**: `UnsupportedClassVersionError`

**Solution**: Ensure you're using Java 25 or later:

```bash
java -version
```

## Status

- [x] Implement core MapStruct path exploring functionality
- [x] Covering all by unit tests with using MapStruct real mappers, and make init stabilization work
- [x] Implement basic one shot runner from cmd (acceptable only for testing or not hard using because of long class path usually, and it will have starting performance penalty because of that)
- [x] Implement lightweight IPC by using Unix Domain Socket for communication by long running applications like NeoVim
- [x] Multi-parameter mapper support with parameter name completion
- [x] Setter detection (JavaBean-style, builder patterns, fluent setters)
- [x] @MappingTarget parameter detection via bytecode analysis
- [x] Smart field kind conversion for target attributes (GETTER→SETTER)
- [x] Full IPC protocol with multi-source API
- [x] Comprehensive test coverage (150+ tests)
- [ ] Testing and stabilization work
- [ ] Create separate nvim plugin `blink-cmp-java-mapstruct` with automatic server installation/updates
