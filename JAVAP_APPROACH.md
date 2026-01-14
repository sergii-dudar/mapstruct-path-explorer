# Using javap to Resolve Class Names - New Approach

## Problem Solved

Previously, the context parser tried to resolve class names by parsing imports with Treesitter. This failed with:
- Wildcard imports (`import com.example.*`)
- Nested class imports (`import com.example.TestClasses.*`)
- Complex import scenarios

Result: **Class not found: Person** (should be `com.dsm.mapstruct.testdata.TestClasses$Person`)

## New Solution: Using javap

Instead of manually resolving imports, we now use `javap` on the **compiled mapper class** to get exact method signatures with fully qualified class names.

## How It Works

### Step 1: Extract Info from Source (Treesitter)
```lua
-- From the Java source file using Treesitter:
- Package: com.dsm.mapstruct.integration.mapper
- Class: TestMapper
- Method: mapComplexNested
- Parameter: person
```

### Step 2: Construct FQCN
```lua
mapper_fqcn = "com.dsm.mapstruct.integration.mapper.TestMapper"
```

### Step 3: Get Classpath from jdtls
```lua
-- Query jdtls for the project classpath (includes all modules + dependencies)
classpath = "target/classes:target/test-classes:..."
```

### Step 4: Run javap
```bash
javap -cp <classpath> com.dsm.mapstruct.integration.mapper.TestMapper
```

**Output:**
```java
Compiled from "TestMapper.java"
public interface com.dsm.mapstruct.integration.mapper.TestMapper {
  public static final com.dsm.mapstruct.integration.mapper.TestMapper INSTANCE;
  public abstract com.dsm.mapstruct.integration.dto.ComplexNestedDTO mapComplexNested(com.dsm.mapstruct.testdata.TestClasses$Person);
  ...
}
```

### Step 5: Parse Method Signature
```lua
-- Extract from: mapComplexNested(com.dsm.mapstruct.testdata.TestClasses$Person);
parameter_type = "com.dsm.mapstruct.testdata.TestClasses$Person"
```

### Step 6: Use Fully Qualified Name
```lua
-- Now we have the exact class name!
-- Send to Java server: com.dsm.mapstruct.testdata.TestClasses$Person
```

## Benefits

✅ **Accurate**: Uses compiled bytecode, not source parsing
✅ **Reliable**: Works with any import style (wildcards, nested classes, etc.)
✅ **Simple**: No complex import resolution logic
✅ **Fast**: javap is very quick
✅ **Correct**: Always gets the exact FQN as Java sees it

## Changes Made

### File: `context.lua`

**New Functions:**
1. `get_mapper_class_info(bufnr)` - Extract package + class name from source
2. `get_jdtls_classpath()` - Get classpath from jdtls LSP
3. `resolve_class_from_javap(bufnr, method_name, param_name)` - Run javap and parse output

**Modified Function:**
- `get_source_class_from_method()` - Now uses javap instead of import resolution

**Removed:**
- Old `resolve_class_name()` function (replaced by javap approach)

## Testing

### Manual Test

You can test javap manually:

```bash
cd /path/to/mapstruct-path-explorer

# Test on the TestMapper
javap -cp target/test-classes:target/classes \
  com.dsm.mapstruct.integration.mapper.TestMapper | grep mapComplexNested
```

**Expected output:**
```
public abstract com.dsm.mapstruct.integration.dto.ComplexNestedDTO mapComplexNested(com.dsm.mapstruct.testdata.TestClasses$Person);
```

### Neovim Test

1. **Restart Neovim** (to load the updated context.lua):
   ```bash
   :qa
   nvim TestMapper.java
   ```

2. **Trigger Completion**:
   - Open `TestMapper.java`
   - Go to line 36: `@Mapping(target = "countryName", source = "address.country.name")`
   - Position cursor after `source = "address.`
   - Type `.` to trigger completion

3. **Check Messages**:
   ```vim
   :messages
   ```

   **Expected logs:**
   ```
   [MapStruct Context] Mapper FQCN: com.dsm.mapstruct.integration.mapper.TestMapper
   [MapStruct Context] Running: javap -cp <classpath> ...
   [MapStruct Context] javap output received
   [MapStruct Context] Found method line: public abstract ... mapComplexNested(...)
   [MapStruct Context] ✓ Extracted parameter type: com.dsm.mapstruct.testdata.TestClasses$Person
   [MapStruct Context] Resolved class: com.dsm.mapstruct.testdata.TestClasses$Person
   [MapStruct Server] Received request - method: explore_path, id: 1
   ```

   **Should NOT see:**
   ```
   ❌ Class not found: Person
   ❌ No import found for 'Person'
   ```

4. **Verify Completion Works**:
   - Should see fields: `address`, `age`, `firstName`, `lastName`, `orders`, `fullName`
   - For nested: `address.` should show `street`, `city`, `state`, `zipCode`, `country`

## Requirements

✅ Compiled classes must exist (run `mvn compile` or `gradle build`)
✅ jdtls must be running and initialized
✅ javap must be available (comes with JDK)

## Troubleshooting

### "javap returned empty output"

**Cause**: Mapper class not compiled or not in classpath

**Solution**:
1. Compile the project: `mvn compile`
2. Wait for jdtls to finish indexing
3. Try `:LspRestart jdtls` then `:MapStructRestart`

### "Could not get classpath from jdtls"

**Cause**: jdtls not running or not initialized

**Solution**:
1. Check `:LspInfo` - should show jdtls attached
2. Wait a few seconds for jdtls to fully start
3. Restart jdtls: `:LspRestart jdtls`

### "Could not find method signature in javap output"

**Cause**: Method name extraction failed or method doesn't exist

**Solution**:
1. Check the method name is correct in the source
2. Ensure the class is compiled
3. Test javap manually (see "Manual Test" above)

## Example Debug Session

```vim
" Position cursor in @Mapping annotation
" Run this to see what's happening:

:lua local ctx = require('utils.blink.mapstruct-source.context'); local result = ctx.get_completion_context(0, vim.fn.line('.') - 1, vim.fn.col('.')); if result then print(string.format("Class: %s\nPath: '%s'", result.class_name, result.path_expression)) else print("No context") end

" Then check messages:
:messages
```

## Performance

- **javap execution**: ~50-100ms (cached by OS)
- **Overall latency**: Acceptable for completion (~100-200ms total)
- **Caching**: Could be added later if needed, but not necessary for now

## Next Steps

1. ✅ Code updated in `context.lua`
2. 🔄 Restart Neovim
3. 🧪 Test with your TestMapper.java
4. 📊 Check `:messages` for debug info
5. ✨ Enjoy working completions!

The javap approach is much more reliable and eliminates all import resolution complexity!
