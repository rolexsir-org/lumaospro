# Managed-Device & Dedicated-Device Deployment (Optional)

LumaOS Pro (`com.lumaos.pro.launcher`) is designed first and foremost as a user-respecting consumer Android home launcher:

- On standard user devices, Android owns default-launcher selection (`RoleManager.ROLE_HOME` on Android 10+ / `Settings.ACTION_HOME_SETTINGS`) and package installation/uninstallation.
- LumaOS Pro **never** attempts fake uninstall prevention, deceptive UI overlays, foreground hijacking, unauthorized accessibility hooks, or silent device-administrator enrollment.
- Standard users can switch default launchers at any time via **LumaOS Pro Settings → Default Home → Switch default launcher** or Android System Settings.

---

## Enterprise / Dedicated-Device Mode (Android `DevicePolicyManager`)

For organization-owned kiosks, retail demo units, hospitality tablets, or MDM-managed fleet devices where persistent launcher assignment and uninstall protection are legitimately required, Android provides official **`DevicePolicyManager`** APIs to an enrolled **Device Owner** DPC (Device Policy Controller) such as Android Management API, TestDPC, or a custom enterprise DPC.

### 1. Persistent Default Home Activity

An active Device Owner DPC can register `HomeActivity` as the persistent preferred `HOME` activity so Android routes `ACTION_MAIN` + `CATEGORY_HOME` directly to LumaOS Pro across reboots without prompting:

```kotlin
val dpm = context.getSystemService(DevicePolicyManager::class.java)
val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
    addCategory(Intent.CATEGORY_HOME)
    addCategory(Intent.CATEGORY_DEFAULT)
}
val lumaHome = ComponentName(
    "com.lumaos.pro.launcher",
    "com.lumaos.pro.launcher.launcher.HomeActivity"
)
dpm.addPersistentPreferredActivity(adminComponent, homeFilter, lumaHome)
```

### 2. Managed Uninstall Protection

To prevent accidental removal of LumaOS Pro on a managed device, the Device Owner DPC marks the package uninstall-blocked:

```kotlin
dpm.setUninstallBlocked(adminComponent, "com.lumaos.pro.launcher", true)
```

### 3. Optional Lock-Task Allowlisting

If the managed fleet uses Android Lock Task mode, include `"com.lumaos.pro.launcher"` alongside approved business applications and system settings/emergency packages in `dpm.setLockTaskPackages(adminComponent, allowedPackages)` so system emergency calls, authentication prompts, and authorized settings remain reachable.

### Releasing Managed Policies

All three policies are cleanly reversible by the organization's Device Owner DPC:

```kotlin
dpm.clearPackagePersistentPreferredActivities(adminComponent, "com.lumaos.pro.launcher")
dpm.setUninstallBlocked(adminComponent, "com.lumaos.pro.launcher", false)
```
