import re

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "r") as f:
    content = f.read()

# Add to TabContent definition
params_old = """    onViewStats: () -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,"""

params_new = """    isCrossfadeEnabled: Boolean,
    onSetCrossfadeEnabled: (Boolean) -> Unit,
    onViewStats: () -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,"""

content = content.replace(params_old, params_new)

# Add to TabContent call inside NaviromApp
call_old = """                        onViewStats = { viewModel.setStatsScreenVisible(true) },
                        onSetLanguage = { viewModel.setLanguage(it) },
                        onSetThemeMode = { viewModel.setThemeMode(it) },"""

call_new = """                        isCrossfadeEnabled = isCrossfadeEnabled,
                        onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                        onViewStats = { viewModel.setStatsScreenVisible(true) },
                        onSetLanguage = { viewModel.setLanguage(it) },
                        onSetThemeMode = { viewModel.setThemeMode(it) },"""

content = content.replace(call_old, call_new)

# Add to TabContent's call to ServerSettingsScreen (which already has it but it says `onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) }`)
setting_call_old = """                isCrossfadeEnabled = isCrossfadeEnabled,
                onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                onViewStats = onViewStats,"""

setting_call_new = """                isCrossfadeEnabled = isCrossfadeEnabled,
                onSetCrossfadeEnabled = onSetCrossfadeEnabled,
                onViewStats = onViewStats,"""
                
content = content.replace(setting_call_old, setting_call_new)

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "w") as f:
    f.write(content)
