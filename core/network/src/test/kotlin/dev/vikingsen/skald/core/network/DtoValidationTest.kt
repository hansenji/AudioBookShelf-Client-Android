package dev.vikingsen.skald.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.BeforeClass
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class DtoValidationTest {

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        private lateinit var libraryId: String
        private var isConnected = false

        private fun findCliScript(): File {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val script = File(dir, "abs-cli.main.kts")
                if (script.exists()) return script
                dir = dir.parentFile
            }
            throw IllegalStateException("Could not find abs-cli.main.kts")
        }

        private fun runCliCommand(vararg args: String): String {
            val script = findCliScript()
            val pb = ProcessBuilder()
            
            val command = mutableListOf<String>()
            val osName = System.getProperty("os.name")?.lowercase() ?: ""
            if (osName.contains("win")) {
                command.add("kotlin")
                command.add(script.absolutePath)
            } else {
                command.add(script.absolutePath)
            }
            command.addAll(args)
            
            pb.command(command)
            pb.directory(script.parentFile)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val outputText = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Command failed with exit status $exitCode.\nOutput: $outputText")
            }
            return outputText
        }

        private fun getCliConfig(): JsonObject? {
            val file = when {
                File("./.abs-cli.json").exists() -> File("./.abs-cli.json")
                else -> File(System.getProperty("user.home"), ".abs-cli.json")
            }
            if (!file.exists()) return null
            return try {
                json.decodeFromString<JsonObject>(file.readText())
            } catch (e: Exception) {
                null
            }
        }

        private fun fetchApi(path: String): String {
            val config = getCliConfig() ?: throw IllegalStateException("CLI config not found")
            val serverUrl = config["serverUrl"]?.jsonPrimitive?.content ?: throw IllegalStateException("serverUrl not set")
            val token = config["token"]?.jsonPrimitive?.content ?: throw IllegalStateException("token not set")
            
            var base = serverUrl.trim().removeSuffix("/")
            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                base = "https://$base"
            }
            
            val client = java.net.http.HttpClient.newHttpClient()
            val request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("$base/${path.removePrefix("/")}"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .GET()
                .build()
                
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                throw RuntimeException("API request failed with status ${response.statusCode()}: ${response.body()}")
            }
            return response.body()
        }

        @BeforeClass
        @JvmStatic
        fun setUp() {
            try {
                val statusJson = runCliCommand("status", "--json")
                val statusObj = json.decodeFromString<JsonObject>(statusJson)
                val statusStr = statusObj["status"]?.jsonPrimitive?.content
                if (statusStr == "connected") {
                    isConnected = true
                    libraryId = statusObj["libraryId"]?.jsonPrimitive?.content ?: ""
                    println("Connected to ABS server. Library ID: $libraryId")
                } else {
                    println("Skipping validation tests: ABS CLI is not connected ($statusStr)")
                }
            } catch (e: Exception) {
                println("Skipping validation tests: Failed to execute status command: ${e.message}")
            }
        }
    }

    @Test
    fun validateLibrariesResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = runCliCommand("libraries", "--json")
        val decoded = json.decodeFromString<LibrariesResponse>(jsonOutput)
        assertNotNull(decoded)
    }

    @Test
    fun validateLibraryItemsResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = runCliCommand("library-items", "--json")
        val decoded = json.decodeFromString<LibraryItemsResponse>(jsonOutput)
        assertNotNull(decoded)

        val firstItem = decoded.results.firstOrNull()
        if (firstItem != null) {
            val itemJson = runCliCommand("item", firstItem.id, "--json")
            val decodedItem = json.decodeFromString<BookResponse>(itemJson)
            assertNotNull(decodedItem)
            
            try {
                val progressJson = runCliCommand("get-progress", firstItem.id, "--json")
                val decodedProgress = json.decodeFromString<MediaProgressResponse>(progressJson)
                assertNotNull(decodedProgress)
            } catch (e: Exception) {
                println("Note: get-progress failed or had different structure: ${e.message}")
            }
        }
    }

    @Test
    fun validateAuthorsResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = runCliCommand("authors", "--json")
        val decoded = json.decodeFromString<AuthorsListResponse>(jsonOutput)
        assertNotNull(decoded)

        val firstAuthor = decoded.authors.firstOrNull()
        if (firstAuthor != null) {
            val authorJson = runCliCommand("author", firstAuthor.id, "--json")
            val decodedAuthor = json.decodeFromString<AuthorDetailsResponse>(authorJson)
            assertNotNull(decodedAuthor)
        }
    }

    @Test
    fun validateSeriesResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = runCliCommand("series", "--json")
        val decoded = json.decodeFromString<SeriesListResponse>(jsonOutput)
        assertNotNull(decoded)
    }

    @Test
    fun validateCollectionsResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = runCliCommand("collections", "--json")
        val decoded = json.decodeFromString<LibraryCollectionsResponse>(jsonOutput)
        assertNotNull(decoded)

        val firstCollection = decoded.results.firstOrNull()
        if (firstCollection != null) {
            val collectionJson = runCliCommand("collection", firstCollection.id, "--json")
            val decodedCollection = json.decodeFromString<NetworkCollectionResponse>(collectionJson)
            assertNotNull(decodedCollection)
        }
    }

    @Test
    fun validatePlaylistsResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = runCliCommand("playlists", "--json")
        val decoded = json.decodeFromString<PlaylistsResponse>(jsonOutput)
        assertNotNull(decoded)

        val firstPlaylist = decoded.playlists.firstOrNull()
        if (firstPlaylist != null) {
            val playlistJson = runCliCommand("playlist", firstPlaylist.id, "--json")
            val decodedPlaylist = json.decodeFromString<NetworkPlaylistResponse>(playlistJson)
            assertNotNull(decodedPlaylist)
        }
    }

    @Test
    fun validateUserProgressResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = fetchApi("api/me")
        val decoded = json.decodeFromString<UserProgressResponse>(jsonOutput)
        assertNotNull(decoded)
    }

    @Test
    fun validatePersonalizedShelvesResponse() {
        org.junit.Assume.assumeTrue(isConnected)
        val jsonOutput = fetchApi("api/libraries/$libraryId/personalized?minified=1&include=rssfeed,numEpisodesIncomplete")
        val decoded = json.decodeFromString<List<NetworkLibraryShelf>>(jsonOutput)
        assertNotNull(decoded)
    }
}
