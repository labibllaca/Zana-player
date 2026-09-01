import os

files = [
    "app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt",
    "app/src/main/java/com/example/navirom/player/NaviromPlaybackService.kt"
]

for file_path in files:
    with open(file_path, "r") as f:
        content = f.read()

    # In NaviromViewModel.kt
    if "private fun buildAlternativeUrl" in content:
        old_func = """    private fun buildAlternativeUrl(protocol: String, alternativeHost: String, port: String): String {
        if (alternativeHost.isBlank()) return ""
        val cleanAlt = alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        return if (port.isNotBlank()) {
            "$protocol://$cleanAlt:$port"
        } else {
            "$protocol://$cleanAlt"
        }
    }"""
        new_func = """    private fun buildAlternativeUrl(protocol: String, alternativeHost: String, port: String): String {
        if (alternativeHost.isBlank()) return ""
        val cleanAlt = alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        if (cleanAlt.contains(":")) {
            return "$protocol://$cleanAlt"
        }
        return if (port.isNotBlank()) {
            "$protocol://$cleanAlt:$port"
        } else {
            "$protocol://$cleanAlt"
        }
    }"""
        content = content.replace(old_func, new_func)
        
    # In NaviromPlaybackService.kt
    old_service_block = """            val altUrl = if (activeConfig.alternativeHost.isNotBlank()) {
                val cleanAlt = activeConfig.alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                if (parsedPort.isNotBlank()) {
                    "$parsedProto://$cleanAlt:$parsedPort"
                } else {
                    "$parsedProto://$cleanAlt"
                }
            } else {
                ""
            }"""
    
    new_service_block = """            val altUrl = if (activeConfig.alternativeHost.isNotBlank()) {
                val cleanAlt = activeConfig.alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                if (cleanAlt.contains(":")) {
                    "$parsedProto://$cleanAlt"
                } else if (parsedPort.isNotBlank()) {
                    "$parsedProto://$cleanAlt:$parsedPort"
                } else {
                    "$parsedProto://$cleanAlt"
                }
            } else {
                ""
            }"""
    content = content.replace(old_service_block, new_service_block)

    with open(file_path, "w") as f:
        f.write(content)
