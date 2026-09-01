import re

with open("app/src/main/java/com/example/navirom/player/AudioPlayerController.kt", "r") as f:
    content = f.read()

content = content.replace("fun next(isCrossfading: Boolean = false) {\n        val q = _queue.value\n        if (q.isEmpty()) return\n        val currentIdx = _currentIndex.value\n\n        when (_playbackState.value.repeatMode) {\n            RepeatMode.ONE -> {\n                seekTo(0)\n                resume()\n            }\n            RepeatMode.ALL -> {\n                _currentIndex.value = (currentIdx + 1) % q.size\n                playCurrentTrack()\n            }\n            RepeatMode.OFF -> {\n                if (currentIdx + 1 < q.size) {\n                    _currentIndex.value = currentIdx + 1\n                    playCurrentTrack()\n                } else {", "fun next(isCrossfading: Boolean = false) {\n        val q = _queue.value\n        if (q.isEmpty()) return\n        val currentIdx = _currentIndex.value\n\n        when (_playbackState.value.repeatMode) {\n            RepeatMode.ONE -> {\n                seekTo(0)\n                resume()\n            }\n            RepeatMode.ALL -> {\n                _currentIndex.value = (currentIdx + 1) % q.size\n                playCurrentTrack(isCrossfading)\n            }\n            RepeatMode.OFF -> {\n                if (currentIdx + 1 < q.size) {\n                    _currentIndex.value = currentIdx + 1\n                    playCurrentTrack(isCrossfading)\n                } else {")

with open("app/src/main/java/com/example/navirom/player/AudioPlayerController.kt", "w") as f:
    f.write(content)
