import os

with open("app/src/main/java/com/example/navirom/player/AudioPlayerController.kt", "r") as f:
    content = f.read()

if "var urlResolver: ((String) -> String)? = null" not in content:
    content = content.replace(
        "private val playbackQueueDao: PlaybackQueueDao\n) {", 
        "private val playbackQueueDao: PlaybackQueueDao\n) {\n    var urlResolver: ((String) -> String)? = null\n"
    )

old_stream_url = "updatedTrack.streamUrl"
new_stream_url = "(urlResolver?.invoke(updatedTrack.streamUrl) ?: updatedTrack.streamUrl)"

if "Uri.parse(updatedTrack.streamUrl)" in content:
    content = content.replace("Uri.parse(updatedTrack.streamUrl)", "Uri.parse(" + new_stream_url + ")")

with open("app/src/main/java/com/example/navirom/player/AudioPlayerController.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "r") as f:
    vm_content = f.read()

if "playerController.urlResolver = " not in vm_content:
    vm_content = vm_content.replace(
        "playerController.isCrossfadeEnabled = _isCrossfadeEnabled.value",
        "playerController.isCrossfadeEnabled = _isCrossfadeEnabled.value\n        playerController.urlResolver = { url -> subsonicClient.resolveUrl(url) }"
    )

with open("app/src/main/java/com/example/navirom/ui/NaviromViewModel.kt", "w") as f:
    f.write(vm_content)

with open("app/src/main/java/com/example/navirom/player/NaviromPlaybackService.kt", "r") as f:
    srv_content = f.read()

if "controller.urlResolver = " not in srv_content:
    srv_content = srv_content.replace(
        "activePlayerController = WeakReference(controller)",
        "controller.urlResolver = { url -> subsonicClient.resolveUrl(url) }\n        activePlayerController = WeakReference(controller)"
    )

with open("app/src/main/java/com/example/navirom/player/NaviromPlaybackService.kt", "w") as f:
    f.write(srv_content)

with open("app/src/main/java/com/example/navirom/data/api/NaviromSubsonicClient.kt", "r") as f:
    client_content = f.read()

resolve_func = """
    fun resolveUrl(url: String): String {
        if (url.isBlank()) return ""
        val activePrefix = activeServerUrl
        if (activePrefix.isBlank()) return url
        
        val primary = serverUrl
        val alt = alternativeServerUrl
        
        if (primary.isNotBlank() && url.startsWith(primary) && activePrefix != primary) {
            return url.replaceFirst(primary, activePrefix)
        }
        if (alt.isNotBlank() && url.startsWith(alt) && activePrefix != alt) {
            return url.replaceFirst(alt, activePrefix)
        }
        return url
    }
"""

if "fun resolveUrl" not in client_content:
    client_content = client_content.replace("fun getStreamUrl(trackId: String): String {", resolve_func + "\n    fun getStreamUrl(trackId: String): String {")

with open("app/src/main/java/com/example/navirom/data/api/NaviromSubsonicClient.kt", "w") as f:
    f.write(client_content)

