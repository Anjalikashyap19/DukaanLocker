# Fix Compilation Error in LockerStorage.kt

The property `id` in `ManagerAccess` is declared in the class body without initialization, causing the compiler error: "Property must be initialized or be abstract".

## Proposed Changes

### DukaanLocker App Core

#### [MODIFY] [LockerStorage.kt](file:///D:/NewCodeBase/Dukaanlocker/android/app/src/main/java/com/example/dukaanlocker/LockerStorage.kt)
- Move `val id: String?` from the class body of `ManagerAccess` to the primary constructor.
- Provide a default value `UUID.randomUUID().toString()` to match the pattern used in `BusinessProfile`.
- Update `saveManagers` to persist the `id` field.
- Update `getManagers` to restore the `id` field.

#### [MODIFY] [DukaanLockerApp.kt](file:///D:/NewCodeBase/Dukaanlocker/android/app/src/main/java/com/example/dukaanlocker/DukaanLockerApp.kt)
- Update mapping from `ManagerResponse` to `ManagerAccess` to explicitly pass the `id` field.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure the project builds successfully.

### Manual Verification
- Verify that managers are correctly displayed in the UI (e.g., in the Add Business screen's manager dropdown) and that their IDs are correctly handled.
