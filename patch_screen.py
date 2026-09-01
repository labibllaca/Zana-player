import re

with open("app/src/main/java/com/example/navirom/ui/screens/ServerSettingsScreen.kt", "r") as f:
    content = f.read()

# Add parameter
param_old = "    statsSummary: ListeningStatsSummary = ListeningStatsSummary(),"
param_new = "    isCrossfadeEnabled: Boolean = false,\n    onSetCrossfadeEnabled: (Boolean) -> Unit = {},\n    statsSummary: ListeningStatsSummary = ListeningStatsSummary(),"
content = content.replace(param_old, param_new)

# Add Crossfade Switch before Library Filter
crossfade_ui = """        Spacer(modifier = Modifier.height(20.dp))

        // Playback Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = str("settings_crossfade"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = isCrossfadeEnabled,
                    onCheckedChange = { onSetCrossfadeEnabled(it) }
                )
            }
        }"""

content = content.replace("        // Library Filter (if server has music folders)", crossfade_ui + "\n\n        // Library Filter (if server has music folders)")

with open("app/src/main/java/com/example/navirom/ui/screens/ServerSettingsScreen.kt", "w") as f:
    f.write(content)
