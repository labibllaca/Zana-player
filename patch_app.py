import re

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "r") as f:
    content = f.read()

# Add collect state
state_old = "    val appThemeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()"
state_new = "    val appThemeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()\n    val isCrossfadeEnabled by viewModel.isCrossfadeEnabled.collectAsStateWithLifecycle()"
content = content.replace(state_old, state_new)

# Update ServerSettingsScreen arguments
args_old = """                onViewStats = onViewStats,
                onSetLanguage = onSetLanguage,
                onSetThemeMode = onSetThemeMode,"""
args_new = """                isCrossfadeEnabled = isCrossfadeEnabled,
                onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                onViewStats = onViewStats,
                onSetLanguage = onSetLanguage,
                onSetThemeMode = onSetThemeMode,"""
content = content.replace(args_old, args_new)

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "w") as f:
    f.write(content)
