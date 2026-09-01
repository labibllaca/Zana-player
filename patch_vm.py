import re

with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "r") as f:
    content = f.read()

# Add isCrossfadeEnabled Flow
prefs_read = """private val _isCrossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val isCrossfadeEnabled: StateFlow<Boolean> = _isCrossfadeEnabled.asStateFlow()

    init {
        playerController.isCrossfadeEnabled = _isCrossfadeEnabled.value"""
content = content.replace("init {\n        loadSavedServerConfig()", prefs_read + "\n        loadSavedServerConfig()")

# Add toggle function
toggle_func = """fun setCrossfadeEnabled(enabled: Boolean) {
        _isCrossfadeEnabled.value = enabled
        playerController.isCrossfadeEnabled = enabled
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
    }"""
content = content.replace("fun setAppThemeMode(themeMode: AppThemeMode) {", toggle_func + "\n\n    fun setAppThemeMode(themeMode: AppThemeMode) {")

with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "w") as f:
    f.write(content)
