package com.fuke.mobile

import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class MediaCompletedOutputTest {
    @Test fun `a final webm is valid but a separate webm track is not`() {
        val root = Files.createTempDirectory("jiexi-output-").toFile()
        try {
            root.resolve("video.f251.webm").writeText("track")
            assertThrows(IllegalArgumentException::class.java) { MediaTaskArtifacts.completedOutput(root, "mp4") }
            val video = root.resolve("video.webm").apply { writeText("finished") }
            assertEquals(video, MediaTaskArtifacts.completedOutput(root, "mp4"))
        } finally { root.deleteRecursively() }
    }
    @Test fun `subtitles thumbnails and unmerged tracks are never selected as the completed video`() {
        val root = Files.createTempDirectory("jiexi-output-").toFile()
        try {
            listOf("movie.en.vtt", "movie.jpg", "movie.f401.mp4", "movie.mp4.part", "movie.info.json").forEach { root.resolve(it).writeText("data") }
            assertThrows(IllegalArgumentException::class.java) { MediaTaskArtifacts.completedOutput(root, "mp4") }
            val final = root.resolve("movie.mp4").apply { writeText("completed") }
            assertEquals(final, MediaTaskArtifacts.completedOutput(root, "mp4"))
            root.resolve("other.mp4").writeText("ambiguous")
            assertThrows(IllegalArgumentException::class.java) { MediaTaskArtifacts.completedOutput(root, "mp4") }
        } finally { root.deleteRecursively() }
    }
    @Test fun `empty files do not count and audio requires mp3`() {
        val root = Files.createTempDirectory("jiexi-output-").toFile()
        try {
            root.resolve("empty.mp3").createNewFile()
            root.resolve("video.mp4").writeText("video")
            assertThrows(IllegalArgumentException::class.java) { MediaTaskArtifacts.completedOutput(root, "mp3") }
            val audio = root.resolve("audio.mp3").apply { writeText("audio") }
            assertEquals(audio, MediaTaskArtifacts.completedOutput(root, "mp3"))
        } finally { root.deleteRecursively() }
    }
}
