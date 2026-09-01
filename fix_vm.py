import os

with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "r") as f:
    content = f.read()

# Add a function to check connection in the background
check_conn_func = """
    fun checkConnectionState() {
        val state = _serverState.value
        if (state.serverUrl.isNotBlank() && state.username.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                // Ping will automatically switch activeServerUrl to alternative if primary fails
                val ping = subsonicClient.ping()
                if (ping.isSuccess) {
                    val activeUrl = subsonicClient.activeServerUrl
                    if (activeUrl.isNotBlank() && activeUrl != state.serverUrl) {
                        // It switched! Update state and library
                        withContext(Dispatchers.Main) {
                            _serverState.update { it.copy(serverUrl = activeUrl) }
                            syncLibrary()
                        }
                    }
                }
            }
        }
    }
"""

if "fun checkConnectionState" not in content:
    content = content.replace("fun scanLocalNetwork() {", check_conn_func + "\n    fun scanLocalNetwork() {")

with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "r") as f:
    app_content = f.read()

# Call checkConnectionState() when app resumes using LifecycleEventObserver
lifecycle_import = "import androidx.lifecycle.LifecycleEventObserver\nimport androidx.compose.ui.platform.LocalLifecycleOwner\nimport androidx.lifecycle.Lifecycle\n"
if "import androidx.lifecycle.LifecycleEventObserver" not in app_content:
    app_content = app_content.replace("import androidx.compose.runtime.*", lifecycle_import + "import androidx.compose.runtime.*\n")

lifecycle_effect = """
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkConnectionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
"""

if "LocalLifecycleOwner.current" not in app_content:
    app_content = app_content.replace("val haptics = rememberNaviromHaptics()", "val haptics = rememberNaviromHaptics()\n" + lifecycle_effect)

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "w") as f:
    f.write(app_content)

