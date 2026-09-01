import re

with open("app/src/main/java/com/example/navirom/ui/NaviromStrings.kt", "r") as f:
    content = f.read()

content = content.replace('"settings_data_usage" to "Data & Storage",', '"settings_data_usage" to "Data & Storage",\n        "settings_crossfade" to "Crossfade between tracks",')
content = content.replace('"settings_data_usage" to "Të dhënat dhe ruajtja",', '"settings_data_usage" to "Të dhënat dhe ruajtja",\n        "settings_crossfade" to "Zbehje midis këngëve",')

with open("app/src/main/java/com/example/navirom/ui/NaviromStrings.kt", "w") as f:
    f.write(content)
