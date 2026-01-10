# Komikku Profiles Feature Specification

**Version:** 3.1 (Final)  
**Date:** 2026-01-10  
**Status:** Ready for Implementation

---

## Overview

Multiple isolated profiles within the app. Each profile has separate library, settings, downloads, and optional PIN lock. Switching profiles recreates the main activity (not process kill) for clean state transition.

**Key Use Cases:**
- Family sharing (Kids profile without adult content)
- Privacy (Hidden profile for sensitive content)
- Testing (Separate profile for trying extensions)

---

## Profile Model

```kotlin
data class Profile(
    val id: String,           // UUID
    val name: String,         // User-defined name
    val color: Int,           // Accent color for visual ID
    val pinHash: String?,     // BCrypt hash, null = no lock
    val isDefault: Boolean,   // Cannot delete, created on migration
    val createdAt: Long,
)
```

**Storage:** JSON file at `shared_prefs/komikku_profiles.json`

---

## Data Isolation Matrix

| Data | Isolated | Storage Location | Notes |
|------|----------|------------------|-------|
| Database (library, history, trackers, repos) | ✅ Yes | `databases/komikku_<id>.db` | Core isolation |
| App preferences (theme, reader, library) | ✅ Yes | `shared_prefs/profile_<id>_prefs.xml` | Per-profile |
| Global preferences (app updates, debug) | ❌ Shared | `shared_prefs/komikku_global.xml` | Device-level |
| Downloads | ✅ Yes | `Komikku/downloads/<id>/` | With `.nomedia` |
| Cover cache | ✅ Yes | `cache/covers_<id>/` | Prevents leakage |
| Chapter cache | ✅ Yes | `cache/chapter_<id>/` | Same reason |
| Coil disk cache | ❌ Shared | `cache/coil/` | Cleared on switch |
| Extensions (APKs) | ❌ Shared | System-managed | Too complex |
| Extension settings | ❌ Shared | Default SharedPrefs | Known limitation |
| Local manga source | ❌ Shared | `Komikku/local/` | Use categories |

---

## Preference Split

### Global (shared across profiles)
- App update checks
- Hardware acceleration
- Debug logging
- DNS over HTTPS
- Extension update checks
- Crash reporting

### Per-Profile
- Everything else (theme, reader, library, trackers, sources, download settings)

---

## Architecture

### Wrapper Pattern

Instead of refactoring all database usages, wrap the handlers:

**ProfileAwareDatabaseHandler:**
```kotlin
class ProfileAwareDatabaseHandler(
    private val context: Context,
    private val profileManager: ProfileManager,
) : DatabaseHandler {
    
    private var currentHandler: AndroidDatabaseHandler? = null
    
    fun initialize() {
        switchTo(profileManager.currentProfileId)
    }
    
    fun switchTo(profileId: String) {
        currentHandler?.close()
        
        val driver = AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = "komikku_$profileId.db",
        )
        val db = Database(driver, /* adapters */)
        currentHandler = AndroidDatabaseHandler(db, driver)
    }
    
    // Delegate all interface methods to currentHandler
    override suspend fun <T> await(inTransaction: Boolean, block: suspend Database.() -> T): T =
        currentHandler!!.await(inTransaction, block)
    
    // ... rest of DatabaseHandler methods
}
```

**ProfileAwarePreferenceStore:**
```kotlin
class ProfileAwarePreferenceStore(
    private val context: Context,
    private val profileManager: ProfileManager,
) : PreferenceStore {
    
    private var currentStore: AndroidPreferenceStore? = null
    
    fun switchTo(profileId: String) {
        val prefs = context.getSharedPreferences(
            "profile_${profileId}_prefs",
            Context.MODE_PRIVATE
        )
        currentStore = AndroidPreferenceStore(context, prefs)
    }
    
    // Delegate all interface methods
    override fun getString(key: String, defaultValue: String) =
        currentStore!!.getString(key, defaultValue)
    // ... rest
}
```

### Why This Works
- Minimal code changes (no refactoring of repositories/interactors)
- All existing code uses DatabaseHandler interface
- Swap implementation underneath, everything keeps working
- Easy to maintain when merging upstream changes

---

## Profile Switching Flow

```
User taps profile
       │
       ▼
┌─────────────────┐
│ Has PIN lock?   │──No──┐
└────────┬────────┘      │
         │Yes            │
         ▼               │
┌─────────────────┐      │
│ Show PIN dialog │      │
│ Verify hash     │      │
└────────┬────────┘      │
         │Success        │
         ▼               ▼
┌─────────────────────────────┐
│ Show "Switching..." overlay │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ ProfileManager.switchTo(id) │
│  1. Save currentProfileId   │
│  2. Cancel all notifications│
│  3. Clear Coil caches       │
│  4. handler.switchTo(id)    │
│  5. prefStore.switchTo(id)  │
│  6. downloadProvider.switch │
│  7. coverCache.switchTo(id) │
│  8. chapterCache.switchTo   │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ Recreate MainActivity       │
│ FLAG_ACTIVITY_NEW_TASK |    │
│ FLAG_ACTIVITY_CLEAR_TASK    │
└──────────────┬──────────────┘
               │
               ▼
    New activity with fresh
    ViewModels, Flows subscribe
    to new profile's database
```

### Why Activity Recreation (not process kill)
- Faster (~1-2 sec vs ~3-5 sec)
- Doesn't look like a crash
- All ViewModels cleared → all Flows cancelled → no stale subscriptions
- Process stays warm

### Why Not True Hot-Swap
- SQLDelight Flows hold Query references to old Database
- Would need to manually invalidate all subscriptions
- Activity recreation is simpler and reliable

---

## Backup & Restore

### Backup Creation
- Filename: `Komikku_<ProfileName>_<date>_<time>.tachibk`
- Show snackbar: "Backing up [Profile Name] profile"
- Only backs up current profile's data

### Restore
- Show warning dialog before restore:
  ```
  "This backup will be restored to your current profile ([Name]). 
   Data in this profile may be overwritten. Continue?"
  ```
- Restores only to current profile

### Files to Modify
- `BackupCreator.kt` - Add profile name to filename
- `BackupCreatorJob.kt` - Show profile in notification
- `RestoreBackupScreen.kt` - Add warning dialog

---

## Notification Handling

On profile switch, before activity recreation:
```kotlin
NotificationManagerCompat.from(context).cancelAll()
```

**Why:** Prevents tapping old notification → crash (manga doesn't exist in new profile)

---

## Storage Structure

```
Internal Storage (/data/data/eu.kanade.tachiyomi/):
├── shared_prefs/
│   ├── komikku_profiles.json       # Profile list, current ID
│   ├── komikku_global.xml          # Global settings
│   ├── profile_<id>_prefs.xml      # Profile-specific settings
│   └── ...
├── databases/
│   ├── komikku_<id>.db             # Profile-specific database
│   └── ...
├── cache/
│   ├── coil/                       # Shared, cleared on switch
│   ├── covers_<id>/                # Profile-specific
│   └── chapter_<id>/               # Profile-specific

External Storage (user-selected):
├── Komikku/
│   ├── downloads/
│   │   ├── <profile_id>/
│   │   │   ├── .nomedia
│   │   │   └── <Source>/<Manga>/<Chapter>/
│   │   └── ...
│   ├── local/                      # Shared
│   └── backups/                    # Shared
```

---

## Migration (Existing Users)

On first launch after update:

```kotlin
fun migrateToProfiles() {
    if (profilesAlreadyExist()) return
    
    val defaultId = UUID.randomUUID().toString()
    
    // 1. Create default profile
    val defaultProfile = Profile(
        id = defaultId,
        name = "Default",
        color = Color.Blue,
        pinHash = null,
        isDefault = true,
        createdAt = System.currentTimeMillis()
    )
    
    // 2. Rename existing database
    context.getDatabasePath("tachiyomi.db")
        .renameTo(context.getDatabasePath("komikku_$defaultId.db"))
    
    // 3. Copy existing prefs to profile prefs
    
    // 4. Move downloads to profile subdirectory + add .nomedia
    
    // 5. Move caches
    
    // 6. Save profile list and set current
}
```

---

## UI Screens

### Profile List (Settings → Profiles)
```
┌─────────────────────────────┐
│ ← Profiles                  │
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │ 🔵 Default        ✓     │ │  ← Current
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ 🟢 Kids           🔒    │ │  ← Has PIN
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ 🟣 Private        🔒    │ │
│ └─────────────────────────┘ │
│                             │
│ [+ Create Profile]          │
└─────────────────────────────┘

Long-press → Edit / Delete
Tap → Switch to profile
```

### Create Profile Dialog
```
┌─────────────────────────────┐
│ Create Profile              │
├─────────────────────────────┤
│ Name: [_______________]     │
│                             │
│ Color: 🔴🟠🟡🟢🔵🟣⚫      │
│                             │
│ ☐ Require PIN to access     │
│   PIN: [____]               │
│   Confirm: [____]           │
│                             │
│ [Cancel]          [Create]  │
└─────────────────────────────┘
```

### Switching Overlay
```
┌─────────────────────────────┐
│                             │
│         🟢                  │
│   Switching to Kids...      │
│                             │
└─────────────────────────────┘
```
Full-screen, profile color background, ~1 sec display.

---

## PIN Lock

### Setting PIN
1. User enables "Require PIN" in create/edit dialog
2. Enter 4-6 digit PIN
3. Confirm PIN
4. Store: `BCrypt.hashpw(pin, BCrypt.gensalt())`

### Verifying PIN
1. User enters PIN
2. Check: `BCrypt.checkpw(enteredPin, storedHash)`
3. Match → proceed | No match → error, stay on current profile

**No biometric in MVP** - Add in v2.

---

## Visual Profile Indicator

Apply profile color to app on startup for instant visual feedback:
```kotlin
// In MainActivity.onCreate()
val profile = profileManager.currentProfile
window.statusBarColor = profile.color
// Or show colored indicator in toolbar
```

---

## Deep Links

1. If only one profile → use it
2. If multiple → use default profile
3. If default locked → prompt PIN first
4. Then handle deep link

---

## Profile Deletion

1. Cannot delete default profile
2. Cannot delete current profile (switch first)
3. Confirm dialog with warning
4. On confirm:
   - Delete database file
   - Delete preference file
   - Delete download directory (recursive)
   - Delete cache directories
   - Remove from profile list

---

## Files to Create

| File | Purpose |
|------|---------|
| `domain/profile/model/Profile.kt` | Data class |
| `domain/profile/repository/ProfileRepository.kt` | Interface |
| `data/profile/ProfileRepositoryImpl.kt` | JSON storage |
| `domain/profile/interactor/ProfileManager.kt` | CRUD + switching |
| `data/ProfileAwareDatabaseHandler.kt` | DB wrapper |
| `core/preference/ProfileAwarePreferenceStore.kt` | Pref wrapper |
| `core/preference/GlobalPreferenceStore.kt` | Global settings |
| `presentation/profile/ProfilesScreen.kt` | List UI |
| `presentation/profile/ProfilesScreenModel.kt` | ViewModel |
| `presentation/profile/CreateProfileDialog.kt` | Create dialog |
| `presentation/profile/PinEntryDialog.kt` | PIN entry |
| `presentation/profile/SwitchingOverlay.kt` | Transition UI |
| `data/profile/ProfileMigration.kt` | One-time migration |

## Files to Modify

| File | Changes |
|------|---------|
| `AppModule.kt` | Register profile-aware handlers |
| `PreferenceModule.kt` | Split global vs profile stores |
| `App.kt` | Initialize ProfileManager, run migration |
| `MainActivity.kt` | Profile color, handle state |
| `DownloadProvider.kt` | Profile-aware paths |
| `CoverCache.kt` | Profile-aware paths |
| `ChapterCache.kt` | Profile-aware paths |
| `StorageManager.kt` | Profile directory creation |
| `SettingsMainScreen.kt` | Add Profiles option |
| `BackupCreator.kt` | Profile name in filename |
| `BackupCreatorJob.kt` | Profile in notification |
| `RestoreBackupScreen.kt` | Warning dialog |
| Navigation files | Add profile routes |

---

## Known Limitations (MVP)

1. Extension settings shared across profiles (login state, source prefs)
2. Background sync only for active profile
3. PIN only, no biometric
4. No "Guest" profile
5. No repo import between profiles
6. Local manga source shared

---

## Future Enhancements (Post-MVP)

1. Biometric lock
2. Multi-profile background sync with notification suppression
3. Guest profile (reset-only)
4. Per-profile extension settings
5. Repo import from other profiles
6. Profile export/import
7. Profile-specific app shortcuts
8. `allowBackgroundSync` and `suppressNotifications` flags

---

## Estimated Effort

| Phase | Tasks | Days |
|-------|-------|------|
| 1. Core | Profile model, ProfileManager, wrappers | 3-4 |
| 2. Storage | Download/cache path changes | 2 |
| 3. UI | Screens, dialogs, switcher | 3 |
| 4. Security | PIN lock | 1-2 |
| 5. Migration | Existing data migration | 1-2 |
| 6. Backup | Filename + warnings | 0.5 |
| 7. Polish | Notifications, visual indicator | 0.5 |
| 8. Testing | Edge cases, bug fixes | 2 |

**Total MVP: ~14-15 days**

---

## Implementation Order

1. `Profile.kt` - Data model
2. `ProfileRepository` + `ProfileRepositoryImpl` - Storage
3. `ProfileManager` - Core logic
4. `ProfileAwareDatabaseHandler` - DB wrapper
5. `ProfileAwarePreferenceStore` - Pref wrapper
6. `GlobalPreferenceStore` - Global prefs
7. Modify `AppModule.kt` - DI registration
8. `ProfileMigration.kt` - Migrate existing users
9. Modify `App.kt` - Initialize + migrate
10. Modify cache/download classes - Profile paths
11. `ProfilesScreen` + `ProfilesScreenModel` - UI
12. `CreateProfileDialog` - Create UI
13. `PinEntryDialog` - Lock UI
14. `SwitchingOverlay` - Transition UI
15. Modify `MainActivity` - Switch handling + visual indicator
16. Modify backup classes - Filename + warnings
17. Testing + bug fixes

---

## Testing Checklist

- [ ] Create profile works
- [ ] Switch profile works (with/without PIN)
- [ ] Data isolation verified (library separate)
- [ ] Settings isolation verified
- [ ] Downloads go to correct directory
- [ ] Covers don't leak between profiles
- [ ] Migration preserves existing data
- [ ] Delete profile cleans up all data
- [ ] Backup includes profile name
- [ ] Restore shows warning
- [ ] Notifications cleared on switch
- [ ] Deep links work with multiple profiles
- [ ] App doesn't crash on any flow

---

## Notes for Future LLMs

- Komikku uses **Injekt** for DI (not Hilt/Koin)
- Database is **SQLDelight** (not Room)
- Preferences use **AndroidPreferenceStore** wrapping SharedPreferences
- The wrapper pattern is chosen to minimize code changes
- Activity recreation is required because SQLDelight Flows hold Query references
- Always test with existing user data (migration path)
- The encrypted database feature (SY) needs consideration during migration
