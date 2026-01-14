# Neovim Integration Fix

## Issue Fixed

**Error**: `attempt to call global 'resolve_class_name' (a nil value)`

**Root Cause**: Function ordering issue in `context.lua` - `resolve_class_name` was defined after `get_source_class_from_method` which called it.

**Solution**: Moved `resolve_class_name` function definition before `get_source_class_from_method`.

## File Changed

`~/.config/nvim/lua/utils/blink/mapstruct-source/context.lua`

## How to Apply Fix

The fix has been automatically applied to your Neovim config. You need to reload Neovim:

### Option 1: Restart Neovim (Recommended)
```bash
# Close all Neovim instances
:qa

# Or from outside Neovim
pkill nvim

# Start Neovim again
nvim your-file.java
```

### Option 2: Reload Lua Module (Without Restarting)
```vim
:lua package.loaded['utils.blink.mapstruct-source.context'] = nil
:lua package.loaded['utils.blink.mapstruct-source.ipc_client'] = nil
:lua package.loaded['utils.blink.mapstruct-source.server'] = nil
:lua package.loaded['utils.blink.mapstruct-source'] = nil
```

Then restart the MapStruct server:
```vim
:MapStructRestart
```

## Verify the Fix

### 1. Check Server Status
```vim
:MapStructStatus
```

Expected output:
```
MapStruct Server Status:
  Running: true
  Connected: true
  ...
```

### 2. Test with a Simple Java File

Create a test file:
```java
package com.example;

import java.util.List;

class Address {
    String street;
    String city;
    String zipCode;
}

class User {
    String name;
    int age;
    Address address;
    List<String> emails;
}

@Mapper
interface TestMapper {
    @Mapping(source = "user.", target = "name")
    //                     ^ Put cursor here and type .
    UserDTO toDto(User user);
}
```

### 3. Trigger Completion

1. Position cursor after `"user.` in the `@Mapping` source
2. Type `.` (dot)
3. Should see completions: `name`, `age`, `address`, `emails`

### 4. Test Nested Path

```java
@Mapping(source = "user.address.", target = "street")
//                              ^ Type . here
```

Should see completions: `street`, `city`, `zipCode`

## Debug Steps if Still Not Working

### 1. Check for Lua Errors
```vim
:messages
```

Look for any errors related to mapstruct-source.

### 2. Check if Server Started
```vim
:MapStructStatus
```

If `Running: false`, try:
```vim
:MapStructRestart
```

### 3. Check jdtls is Running
```vim
:LspInfo
```

Should see `jdtls` client attached to the buffer.

### 4. Check Treesitter is Working

```vim
:lua print(vim.treesitter.get_parser(0, "java") and "OK" or "FAIL")
```

Should print `OK`.

### 5. Test Context Detection

```vim
:lua local ctx = require('utils.blink.mapstruct-source.context'); print(vim.inspect(ctx.get_completion_context(0, vim.fn.line('.') - 1, vim.fn.col('.'))))
```

Position cursor in a `@Mapping` annotation and run this. Should print context info like:
```lua
{
  attribute_type = "source",
  class_name = "com.example.User",
  col = 35,
  line = 15,
  path_expression = ""
}
```

If it prints `nil`, you're not in a valid MapStruct annotation context.

### 6. Manual Server Test

Test the server outside Neovim:
```bash
# Terminal 1: Start server
java -cp ~/serhii.home/personal/git/mapstruct-path-explorer/target/mapstruct-path-explorer.jar \
     com.dsm.mapstruct.IpcServer /tmp/test-mapstruct.sock

# Terminal 2: Send test request
echo '{"id":"1","method":"ping","params":{}}' | nc -U /tmp/test-mapstruct.sock
```

Should receive: `{"id":"1","result":{"message":"pong"}}`

## Common Issues

### Issue 1: "No completions appearing"
- **Check**: Are you inside the quotes of `source = "..."` or `target = "..."`?
- **Check**: Did you type `.` (dot) to trigger completion?
- **Check**: Is jdtls running? (`:LspInfo`)

### Issue 2: "Server not starting"
- **Check**: Java version (needs 17+): `java -version`
- **Check**: JAR exists: `:echo filereadable(expand('~/serhii.home/personal/git/mapstruct-path-explorer/target/mapstruct-path-explorer.jar'))`
- **Check**: Messages for errors: `:messages`

### Issue 3: "Class not found errors"
- **Check**: Project is compiled: `mvn compile` or `gradle build`
- **Check**: jdtls is fully initialized (wait a few seconds after opening file)
- **Try**: `:LspRestart jdtls` then `:MapStructRestart`

### Issue 4: "Context detection not working"
- **Check**: You're in a `@Mapping` annotation
- **Check**: The annotation format is correct: `@Mapping(source = "path", target = "field")`
- **Check**: Method signature has a parameter matching the class type

## Expected Behavior

When working correctly, you should see:

1. **Cursor in annotation**: Typing `.` after a field name
2. **Completion menu appears**: Within ~100ms
3. **Shows fields**: With icons, types, and kinds
4. **Source badge**: `[MS]` indicating MapStruct source
5. **Hover documentation**: Full details about the field

Example completion menu:
```
┌────────────────────────────────────────────┐
│ 󰜢 name       String      Field      [MS] │
│ 󰜢 age        int         Field      [MS] │
│ 󰜢 address    Address     Field      [MS] │
│ 󰜢 emails     List        Field      [MS] │
└────────────────────────────────────────────┘
```

## Next Steps

1. ✅ Fix applied to `context.lua`
2. 🔄 Restart Neovim
3. 🧪 Test with a Java file
4. 📝 Report if you encounter any other issues

The function ordering issue has been resolved. The completion should now work correctly!
