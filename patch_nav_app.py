import re

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "r") as f:
    content = f.read()

old_call = """                            statsSummary = listeningStats,
                            onViewStats = { viewModel.setStatsScreenVisible(true) },
                            onSetLanguage = { viewModel.setLanguage(it) },"""

new_call = """                            statsSummary = listeningStats,
                            isCrossfadeEnabled = isCrossfadeEnabled,
                            onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                            onViewStats = { viewModel.setStatsScreenVisible(true) },
                            onSetLanguage = { viewModel.setLanguage(it) },"""

content = content.replace(old_call, new_call)

with open("app/src/main/java/com/example/navirom/ui/NaviromApp.kt", "w") as f:
    f.write(content)
