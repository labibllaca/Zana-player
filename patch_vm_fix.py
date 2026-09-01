import re
with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "r") as f:
    content = f.read()
toggle_func = """fun setCrossfadeEnabled(enabled: Boolean) {
        _isCrossfadeEnabled.value = enabled
        playerController.isCrossfadeEnabled = enabled
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
    }"""
content = content.replace("fun setThemeMode(themeMode: AppThemeMode) {", toggle_func + "\n\n    fun setThemeMode(themeMode: AppThemeMode) {")
with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "w") as f:
    f.write(content)
