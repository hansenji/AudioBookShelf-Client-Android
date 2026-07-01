#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2")
@file:DependsOn("com.google.code.gson:gson:2.10.1")

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

// ==========================================
// Config & Settings
// ==========================================
data class Config(
    val serverUrl: String? = null,
    val token: String? = null,
    val username: String? = null,
    val libraryId: String? = null
)

val gson: Gson = GsonBuilder().setPrettyPrinting().create()

fun loadConfig(configPath: String?): Config {
    val file = getConfigFile(configPath)
    if (!file.exists()) {
        return Config()
    }
    return try {
        gson.fromJson(file.readText(), Config::class.java) ?: Config()
    } catch (e: Exception) {
        System.err.println("Warning: Failed to parse config file: ${e.message}")
        Config()
    }
}

fun saveConfig(config: Config, configPath: String?) {
    val file = getConfigFile(configPath)
    try {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(gson.toJson(config))
        System.err.println("Configuration saved to ${file.absolutePath}")
    } catch (e: Exception) {
        System.err.println("Error: Failed to save config file: ${e.message}")
    }
}

fun getConfigFile(configPath: String?): File {
    return when {
        configPath != null -> File(configPath)
        File("./.abs-cli.json").exists() -> File("./.abs-cli.json")
        else -> File(System.getProperty("user.home"), ".abs-cli.json")
    }
}

// ==========================================
// CLI Argument Parser
// ==========================================
class CommandArgs(args: Array<String>) {
    var action: String? = null
    val actionParams = mutableListOf<String>()
    val options = mutableMapOf<String, String>()
    var json = false
    var help = false
    var server: String? = null
    var token: String? = null
    var user: String? = null
    var pass: String? = null
    var configPath: String? = null

    init {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("-")) {
                val raw = arg.removePrefix("-").removePrefix("-")
                val key: String
                val value: String?
                
                if (raw.contains("=")) {
                    val parts = raw.split("=", limit = 2)
                    key = parts[0]
                    value = parts[1]
                } else {
                    key = raw
                    if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        value = args[i + 1]
                    } else {
                        value = null
                    }
                }
                
                when (key) {
                    "h", "help" -> {
                        help = true
                        i++
                    }
                    "json" -> {
                        json = true
                        i++
                    }
                    "s", "server" -> {
                        if (value != null) {
                            server = value
                            i += if (raw.contains("=")) 1 else 2
                        } else {
                            System.err.println("Error: Missing value for option $arg")
                            exitProcess(1)
                        }
                    }
                    "t", "token" -> {
                        if (value != null) {
                            token = value
                            i += if (raw.contains("=")) 1 else 2
                        } else {
                            System.err.println("Error: Missing value for option $arg")
                            exitProcess(1)
                        }
                    }
                    "u", "user" -> {
                        if (value != null) {
                            user = value
                            i += if (raw.contains("=")) 1 else 2
                        } else {
                            System.err.println("Error: Missing value for option $arg")
                            exitProcess(1)
                        }
                    }
                    "p", "password" -> {
                        if (value != null) {
                            pass = value
                            i += if (raw.contains("=")) 1 else 2
                        } else {
                            System.err.println("Error: Missing value for option $arg")
                            exitProcess(1)
                        }
                    }
                    "c", "config" -> {
                        if (value != null) {
                            configPath = value
                            i += if (raw.contains("=")) 1 else 2
                        } else {
                            System.err.println("Error: Missing value for option $arg")
                            exitProcess(1)
                        }
                    }
                    else -> {
                        if (value != null) {
                            options[key] = value
                            i += if (raw.contains("=")) 1 else 2
                        } else {
                            options[key] = "true"
                            i++
                        }
                    }
                }
            } else {
                if (action == null) {
                    action = arg
                } else {
                    actionParams.add(arg)
                }
                i++
            }
        }
    }
}

// ==========================================
// HTTP client & Audiobookshelf API
// ==========================================
class AbsClient(
    val serverUrl: String?,
    val token: String?
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun sendRequest(
        method: String,
        path: String,
        body: String? = null
    ): HttpResponse<String> {
        val baseUrl = serverUrl?.trim()?.removeSuffix("/") 
            ?: throw IllegalStateException("Server URL not configured. Use 'login' or set --server")
        
        var formattedBaseUrl = baseUrl
        if (!formattedBaseUrl.startsWith("http://") && !formattedBaseUrl.startsWith("https://")) {
            formattedBaseUrl = "https://$formattedBaseUrl"
        }

        val url = if (path.startsWith("http")) path else "$formattedBaseUrl/${path.removePrefix("/")}"
        
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        val bodyPublisher = if (body != null) {
            HttpRequest.BodyPublishers.ofString(body)
        } else {
            HttpRequest.BodyPublishers.noBody()
        }
        
        requestBuilder.method(method, bodyPublisher)
        
        return try {
            client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            System.err.println("Error: Failed to connect to server at $url: ${e.message}")
            exitProcess(2)
        }
    }
}

// ==========================================
// UI & Table Formatter
// ==========================================
fun printTable(headers: List<String>, rows: List<List<String>>) {
    if (rows.isEmpty()) {
        println("No entries found.")
        return
    }
    val colWidths = headers.map { it.length }.toMutableList()
    for (row in rows) {
        for (i in 0 until minOf(colWidths.size, row.size)) {
            colWidths[i] = maxOf(colWidths[i], row[i].length)
        }
    }
    
    val formatString = colWidths.joinToString("   ") { "%-${it}s" }
    println(String.format(formatString, *headers.toTypedArray()))
    println(colWidths.joinToString("   ") { "-".repeat(it) })
    for (row in rows) {
        val paddedRow = row + List(maxOf(0, headers.size - row.size)) { "" }
        println(String.format(formatString, *paddedRow.toTypedArray()))
    }
}

fun printHelp() {
    val helpText = """
Audiobookshelf CLI (Kotlin Script)
A command line utility to interact with the Audiobookshelf API. Extremely AI-agent friendly.

Usage:
  ./abs-cli.main.kts <action> [args...] [options]

Global Options:
  -h, --help            Show this help message
  -s, --server <url>    Specify or override the Audiobookshelf server URL
  -t, --token <token>   Specify or override the API auth token
  -c, --config <path>   Specify a custom config file path (default: ~/.abs-cli.json)
  --json                Output raw JSON response (ideal for scripts and AI agents)

Actions:
  login                 Authenticate with the server and save credentials to config.
                        Requires: -s/--server, -u/--user, -p/--password
                        Example: ./abs-cli.main.kts login -s http://localhost:8000 -u myuser -p mypass

  status                Print current connection details and connection status.
                        Example: ./abs-cli.main.kts status

  libraries             List all libraries on the Audiobookshelf server.
                        Example: ./abs-cli.main.kts libraries

  use-library <id>      Set the default/active library to use for subsequent commands.
                        Alias: use <id>
                        Example: ./abs-cli.main.kts use-library lib_123

  library-items [id]    List items in a library (falls back to active library).
                        Options: --limit <n>, --page <n>
                        Example: ./abs-cli.main.kts library-items --limit 5

  item <id>             Get detailed metadata, files, and chapters for a library item.
                        Example: ./abs-cli.main.kts item item_456

  search <query>        Search items within a library (falls back to active library).
                        Options: --library <id>, --limit <n>
                        Example: ./abs-cli.main.kts search "Stephen King" --limit 5

  get-progress <id>     Get playback progress for a library item.
                        Example: ./abs-cli.main.kts get-progress item_456

  sync-progress <id>    Update playback progress for a library item.
                        Options: --currentTime <seconds> (required), 
                                 --duration <seconds> (optional), 
                                 --progress <0.0-1.0> (optional),
                                 --finished <true|false> (optional)
                        Example: ./abs-cli.main.kts sync-progress item_456 --currentTime 1500 --duration 3000 --finished false

  playlists             List all playlists.
                        Example: ./abs-cli.main.kts playlists

  playlist <id>         Get details of a specific playlist.
                        Example: ./abs-cli.main.kts playlist playlist_789

  collections [id]      List collections in a library (falls back to active library).
                        Example: ./abs-cli.main.kts collections

  collection <id>       Get details of a specific collection.
                        Example: ./abs-cli.main.kts collection col_999

  authors [id]          List authors in a library (falls back to active library).
                        Example: ./abs-cli.main.kts authors

  author <id>           Get details of a specific author (including their books).
                        Example: ./abs-cli.main.kts author auth_111

  series [id]           List series in a library (falls back to active library),
                        or get details of a specific series if a series ID is provided.
                        Example: ./abs-cli.main.kts series
"""
    println(helpText.trimIndent())
}

// ==========================================
// Main Script Execution Entry Point
// ==========================================
val commandArgs = CommandArgs(args)

if (commandArgs.help || (commandArgs.action == null && commandArgs.server == null && commandArgs.token == null)) {
    printHelp()
    exitProcess(0)
}

val config = loadConfig(commandArgs.configPath)
val activeServerUrl = commandArgs.server ?: config.serverUrl
val activeToken = commandArgs.token ?: config.token

val client = AbsClient(activeServerUrl, activeToken)

fun checkResponse(response: HttpResponse<String>, actionName: String): JsonElement {
    if (response.statusCode() >= 400) {
        System.err.println("Error: Action '$actionName' failed with HTTP Status ${response.statusCode()}")
        System.err.println("Response body: ${response.body()}")
        exitProcess(2)
    }
    return try {
        JsonParser.parseString(response.body())
    } catch (e: Exception) {
        System.err.println("Error: Failed to parse server response as JSON: ${e.message}")
        System.err.println("Raw response: ${response.body()}")
        exitProcess(2)
    }
}

val action = commandArgs.action ?: "status"

when (action.lowercase()) {
    "login" -> {
        val server = commandArgs.server ?: config.serverUrl
        val user = commandArgs.user ?: config.username
        val pass = commandArgs.pass
        
        if (server == null || user == null || pass == null) {
            System.err.println("Error: login requires --server, --user, and --password")
            exitProcess(1)
        }
        
        val loginClient = AbsClient(server, null)
        val loginBody = JsonObject().apply {
            addProperty("username", user)
            addProperty("password", pass)
        }
        
        System.err.println("Connecting to $server...")
        val response = loginClient.sendRequest("POST", "login", gson.toJson(loginBody))
        val json = checkResponse(response, "login").asJsonObject
        
        val userObj = json.getAsJsonObject("user")
        val token = when {
            userObj.has("token") -> userObj.get("token").asString
            userObj.has("accessToken") -> userObj.get("accessToken").asString
            else -> throw Exception("Authentication succeeded but no API token was returned.")
        }
        
        val newConfig = Config(
            serverUrl = server,
            token = token,
            username = user,
            libraryId = config.libraryId
        )
        saveConfig(newConfig, commandArgs.configPath)
        
        if (commandArgs.json) {
            println(gson.toJson(newConfig))
        } else {
            println("Login successful! Token acquired and saved for user: $user")
        }
    }
    
    "status" -> {
        if (activeServerUrl == null || activeToken == null) {
            val statusJson = JsonObject().apply {
                addProperty("status", "disconnected")
                addProperty("reason", "No server URL or token configured. Please run 'login' action first.")
            }
            if (commandArgs.json) {
                println(gson.toJson(statusJson))
            } else {
                println("Status: Disconnected")
                println("Please run: ./abs-cli.main.kts login -s <server-url> -u <user> -p <pass>")
            }
            exitProcess(1)
        }
        
        // Verify token via /api/me
        val response = client.sendRequest("GET", "api/me")
        if (response.statusCode() == 200) {
            val json = JsonParser.parseString(response.body()).asJsonObject
            val username = json.get("username").asString
            val statusJson = JsonObject().apply {
                addProperty("status", "connected")
                addProperty("serverUrl", activeServerUrl)
                addProperty("username", username)
                addProperty("libraryId", config.libraryId)
            }
            if (commandArgs.json) {
                println(gson.toJson(statusJson))
            } else {
                println("Status: Connected")
                println("Server:  $activeServerUrl")
                println("User:    $username")
                println("Library: ${config.libraryId ?: "None configured (run 'use-library')"}")
            }
        } else {
            val statusJson = JsonObject().apply {
                addProperty("status", "invalid_token")
                addProperty("serverUrl", activeServerUrl)
                addProperty("statusCode", response.statusCode())
            }
            if (commandArgs.json) {
                println(gson.toJson(statusJson))
            } else {
                println("Status: Connection Failed (Invalid Token or Server Offline)")
                println("Server: $activeServerUrl")
                println("HTTP Status: ${response.statusCode()}")
            }
            exitProcess(1)
        }
    }
    
    "use-library", "use" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: use-library requires a library ID.")
            System.err.println("Usage: ./abs-cli.main.kts use-library <library-id>")
            exitProcess(1)
        }
        val targetLibraryId = commandArgs.actionParams[0]
        
        System.err.println("Verifying library ID '$targetLibraryId' with server...")
        val response = client.sendRequest("GET", "api/libraries")
        val json = checkResponse(response, "use-library").asJsonObject
        val libraries = json.getAsJsonArray("libraries")
        
        var found = false
        var libraryName = ""
        for (lib in libraries) {
            val l = lib.asJsonObject
            if (l.get("id").asString == targetLibraryId) {
                found = true
                libraryName = l.get("name").asString
                break
            }
        }
        
        if (!found) {
            System.err.println("Error: Library ID '$targetLibraryId' was not found on the server.")
            exitProcess(1)
        }
        
        val newConfig = config.copy(libraryId = targetLibraryId)
        saveConfig(newConfig, commandArgs.configPath)
        
        val successJson = JsonObject().apply {
            addProperty("status", "success")
            addProperty("libraryId", targetLibraryId)
            addProperty("libraryName", libraryName)
        }
        
        if (commandArgs.json) {
            println(gson.toJson(successJson))
        } else {
            println("Now using library: $libraryName ($targetLibraryId)")
        }
    }

    "libraries" -> {
        val response = client.sendRequest("GET", "api/libraries")
        val json = checkResponse(response, "libraries").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val libraries = json.getAsJsonArray("libraries")
            val headers = listOf("Active", "ID", "Name", "Type")
            val rows = libraries.map { lib ->
                val l = lib.asJsonObject
                val id = l.get("id").asString
                val isActive = if (id == config.libraryId) "*" else ""
                listOf(
                    isActive,
                    id,
                    l.get("name").asString,
                    l.get("type")?.asString ?: "N/A"
                )
            }
            println("Libraries:")
            printTable(headers, rows)
        }
    }
    
    "library-items" -> {
        val libraryId = when {
            commandArgs.actionParams.isNotEmpty() -> commandArgs.actionParams[0]
            config.libraryId != null -> config.libraryId
            else -> {
                System.err.println("Error: library-items requires a library ID as an argument or a saved active library.")
                System.err.println("Usage: ./abs-cli.main.kts library-items [library-id]")
                exitProcess(1)
            }
        }
        val limit = commandArgs.options["limit"] ?: "50"
        val page = commandArgs.options["page"] ?: "0"
        
        val path = "api/libraries/$libraryId/items?limit=$limit&page=$page"
        val response = client.sendRequest("GET", path)
        val json = checkResponse(response, "library-items").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val results = json.getAsJsonArray("results")
            val total = json.get("total")?.asInt ?: results.size()
            val headers = listOf("ID", "Title", "Author", "Narrator")
            val rows = results.map { item ->
                val obj = item.asJsonObject
                val media = obj.getAsJsonObject("media")
                val metadata = media?.getAsJsonObject("metadata")
                listOf(
                    obj.get("id").asString,
                    metadata?.get("title")?.asString ?: "N/A",
                    metadata?.get("authorName")?.asString ?: "N/A",
                    metadata?.get("narratorName")?.asString ?: "N/A"
                )
            }
            println("Library Items (Page $page, showing ${results.size()} of $total):")
            printTable(headers, rows)
        }
    }
    
    "item" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: item requires an item ID.")
            System.err.println("Usage: ./abs-cli.main.kts item <item-id>")
            exitProcess(1)
        }
        val itemId = commandArgs.actionParams[0]
        val response = client.sendRequest("GET", "api/items/$itemId")
        val json = checkResponse(response, "item").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val media = json.getAsJsonObject("media")
            val metadata = media?.getAsJsonObject("metadata")
            
            println("Item Details:")
            println("  ID:        $itemId")
            println("  Title:     ${metadata?.get("title")?.asString ?: "N/A"}")
            println("  Subtitle:  ${metadata?.get("subtitle")?.asString ?: "N/A"}")
            println("  Author:    ${metadata?.get("authorName")?.asString ?: "N/A"}")
            println("  Narrator:  ${metadata?.get("narratorName")?.asString ?: "N/A"}")
            println("  Publisher: ${metadata?.get("publisher")?.asString ?: "N/A"}")
            println("  Year:      ${metadata?.get("publishedYear")?.asString ?: "N/A"}")
            
            val chapters = media?.getAsJsonArray("chapters")
            if (chapters != null && chapters.size() > 0) {
                println("\nChapters (${chapters.size()}):")
                val chapHeaders = listOf("Index", "Title", "Start Time (s)", "End Time (s)")
                val chapRows = chapters.mapIndexed { idx, chap ->
                    val c = chap.asJsonObject
                    listOf(
                        (idx + 1).toString(),
                        c.get("title").asString,
                        c.get("start").asDouble.toString(),
                        c.get("end").asDouble.toString()
                    )
                }
                printTable(chapHeaders, chapRows)
            }
        }
    }
    
    "search" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: search requires a search query.")
            System.err.println("Usage: ./abs-cli.main.kts search <query> [options]")
            exitProcess(1)
        }
        val libraryId = when {
            commandArgs.options.containsKey("library") -> commandArgs.options["library"]
            commandArgs.options.containsKey("l") -> commandArgs.options["l"]
            config.libraryId != null -> config.libraryId
            else -> {
                System.err.println("Error: search requires a library ID. Set a default library with 'use-library' first, or pass --library <id>")
                exitProcess(1)
            }
        }
        val query = commandArgs.actionParams.joinToString(" ")
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val limit = commandArgs.options["limit"]
        val path = if (limit != null) {
            "api/libraries/$libraryId/search?q=$encodedQuery&limit=$limit&minified=1"
        } else {
            "api/libraries/$libraryId/search?q=$encodedQuery"
        }
        val response = client.sendRequest("GET", path)
        val json = checkResponse(response, "search").asJsonObject
        
        if (limit != null) {
            val limitVal = limit.toIntOrNull()
            if (limitVal != null && limitVal > 0) {
                listOf("book", "podcast", "authors", "series", "tags").forEach { key ->
                    if (json.has(key) && json.get(key).isJsonArray) {
                        val arr = json.getAsJsonArray(key)
                        if (arr.size() > limitVal) {
                            val newArr = JsonArray()
                            for (idx in 0 until limitVal) {
                                newArr.add(arr.get(idx))
                            }
                            json.add(key, newArr)
                        }
                    }
                }
            }
        }
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            println("Search Results for '$query':")
            val books = json.getAsJsonArray("book")
            if (books != null && books.size() > 0) {
                println("\nBooks:")
                val headers = listOf("ID", "Title", "Author")
                val rows = books.map { b ->
                    val item = b.asJsonObject.getAsJsonObject("libraryItem")
                    val metadata = item?.getAsJsonObject("media")?.getAsJsonObject("metadata")
                    listOf(
                        item?.get("id")?.asString ?: "N/A",
                        metadata?.get("title")?.asString ?: "N/A",
                        metadata?.get("authorName")?.asString ?: "N/A"
                    )
                }
                printTable(headers, rows)
            } else {
                println("\nNo books found.")
            }
        }
    }
    
    "get-progress" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: get-progress requires an item ID.")
            System.err.println("Usage: ./abs-cli.main.kts get-progress <item-id>")
            exitProcess(1)
        }
        val itemId = commandArgs.actionParams[0]
        val response = client.sendRequest("GET", "api/me/progress/$itemId")
        
        if (response.statusCode() == 404) {
            val emptyProgress = JsonObject().apply {
                addProperty("currentTime", 0.0)
                addProperty("progress", 0.0)
                addProperty("isFinished", false)
            }
            if (commandArgs.json) {
                println(gson.toJson(emptyProgress))
            } else {
                println("Playback progress not found (never started or deleted).")
                println("Current Time: 0s")
                println("Progress:     0%")
                println("Finished:     false")
            }
        } else {
            val json = checkResponse(response, "get-progress").asJsonObject
            if (commandArgs.json) {
                println(gson.toJson(json))
            } else {
                val time = json.get("currentTime").asDouble
                val progress = json.get("progress").asFloat * 100
                val isFinished = json.get("isFinished").asBoolean
                println("Playback Progress:")
                println("  Current Time: ${time}s")
                println("  Progress:     ${String.format("%.2f", progress)}%")
                println("  Finished:     $isFinished")
            }
        }
    }
    
    "sync-progress" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: sync-progress requires an item ID.")
            System.err.println("Usage: ./abs-cli.main.kts sync-progress <item-id> --currentTime <seconds> [options]")
            exitProcess(1)
        }
        val itemId = commandArgs.actionParams[0]
        val currentTimeStr = commandArgs.options["currentTime"]
        if (currentTimeStr == null) {
            System.err.println("Error: --currentTime option is required for sync-progress.")
            exitProcess(1)
        }
        
        val currentTime = currentTimeStr.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid value for --currentTime: $currentTimeStr")
            
        val durationStr = commandArgs.options["duration"]
        val duration = durationStr?.toDoubleOrNull()
        
        val isFinished = commandArgs.options["finished"]?.toBoolean() ?: false
        
        val progressVal = when {
            commandArgs.options.containsKey("progress") -> {
                commandArgs.options["progress"]?.toFloatOrNull() ?: 0.0f
            }
            duration != null && duration > 0 -> {
                (currentTime / duration).toFloat()
            }
            else -> 0.0f
        }
        
        val progressPayload = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("libraryItemId", itemId)
                addProperty("currentTime", currentTime)
                addProperty("progress", progressVal)
                addProperty("isFinished", isFinished)
            })
        }
        
        val response = client.sendRequest("POST", "api/me/progress", gson.toJson(progressPayload))
        if (response.statusCode() == 200 || response.statusCode() == 204) {
            val successJson = JsonObject().apply {
                addProperty("status", "success")
                addProperty("libraryItemId", itemId)
                addProperty("currentTime", currentTime)
                addProperty("progress", progressVal)
                addProperty("isFinished", isFinished)
            }
            if (commandArgs.json) {
                println(gson.toJson(successJson))
            } else {
                println("Progress synced successfully!")
                println("  Time:     ${currentTime}s")
                println("  Progress: ${String.format("%.2f", progressVal * 100)}%")
                println("  Finished: $isFinished")
            }
        } else {
            System.err.println("Error: Progress sync failed with Status ${response.statusCode()}")
            System.err.println(response.body())
            exitProcess(2)
        }
    }
    
    "playlists" -> {
        val response = client.sendRequest("GET", "api/playlists")
        val json = checkResponse(response, "playlists").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val playlists = json.getAsJsonArray("playlists")
            val headers = listOf("ID", "Name", "Description", "Duration (s)", "Items Count")
            val rows = playlists.map { p ->
                val obj = p.asJsonObject
                listOf(
                    obj.get("id").asString,
                    obj.get("name").asString,
                    obj.get("description")?.asString ?: "None",
                    obj.get("totalDuration")?.asDouble?.toString() ?: "0.0",
                    obj.getAsJsonArray("items")?.size()?.toString() ?: "0"
                )
            }
            println("Playlists:")
            printTable(headers, rows)
        }
    }
    
    "playlist" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: playlist requires a playlist ID.")
            System.err.println("Usage: ./abs-cli.main.kts playlist <playlist-id>")
            exitProcess(1)
        }
        val playlistId = commandArgs.actionParams[0]
        val response = client.sendRequest("GET", "api/playlists/$playlistId")
        val json = checkResponse(response, "playlist").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            println("Playlist Details:")
            println("  ID:          $playlistId")
            println("  Name:        ${json.get("name").asString}")
            println("  Description: ${json.get("description")?.asString ?: "None"}")
            println("  Duration:    ${json.get("totalDuration")?.asDouble ?: 0.0}s")
            
            val items = json.getAsJsonArray("items")
            if (items != null && items.size() > 0) {
                println("\nPlaylist Items:")
                val itemHeaders = listOf("Sequence", "Item ID", "Title", "Author")
                val itemRows = items.map { item ->
                    val obj = item.asJsonObject
                    val libItem = obj.getAsJsonObject("libraryItem")
                    val media = libItem?.getAsJsonObject("media")
                    val metadata = media?.getAsJsonObject("metadata")
                    listOf(
                        obj.get("sequence")?.asInt?.toString() ?: "N/A",
                        obj.get("libraryItemId").asString,
                        metadata?.get("title")?.asString ?: "N/A",
                        metadata?.get("authorName")?.asString ?: "N/A"
                    )
                }
                printTable(itemHeaders, itemRows)
            }
        }
    }
    
    "collections" -> {
        val libraryId = when {
            commandArgs.actionParams.isNotEmpty() -> commandArgs.actionParams[0]
            config.libraryId != null -> config.libraryId
            else -> {
                System.err.println("Error: collections requires a library ID as an argument or a saved active library.")
                System.err.println("Usage: ./abs-cli.main.kts collections [library-id]")
                exitProcess(1)
            }
        }
        val response = client.sendRequest("GET", "api/libraries/$libraryId/collections?minified=1")
        val json = checkResponse(response, "collections").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val results = json.getAsJsonArray("results")
            val headers = listOf("ID", "Name", "Description", "Items Count")
            val rows = results.map { c ->
                val obj = c.asJsonObject
                listOf(
                    obj.get("id").asString,
                    obj.get("name").asString,
                    obj.get("description")?.asString ?: "None",
                    obj.getAsJsonArray("books")?.size()?.toString() ?: "0"
                )
            }
            println("Collections:")
            printTable(headers, rows)
        }
    }
    
    "collection" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: collection requires a collection ID.")
            System.err.println("Usage: ./abs-cli.main.kts collection <collection-id>")
            exitProcess(1)
        }
        val collectionId = commandArgs.actionParams[0]
        val response = client.sendRequest("GET", "api/collections/$collectionId")
        val json = checkResponse(response, "collection").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            println("Collection Details:")
            println("  ID:          $collectionId")
            println("  Name:        ${json.get("name").asString}")
            println("  Description: ${json.get("description")?.asString ?: "None"}")
            
            val books = json.getAsJsonArray("books")
            if (books != null && books.size() > 0) {
                println("\nCollection Items:")
                val bookHeaders = listOf("Book ID", "Title", "Author")
                val bookRows = books.map { b ->
                    val obj = b.asJsonObject
                    val media = obj.getAsJsonObject("media")
                    val metadata = media?.getAsJsonObject("metadata")
                    listOf(
                        obj.get("id").asString,
                        metadata?.get("title")?.asString ?: "N/A",
                        metadata?.get("authorName")?.asString ?: "N/A"
                    )
                }
                printTable(bookHeaders, bookRows)
            }
        }
    }
    
    "authors" -> {
        val libraryId = when {
            commandArgs.actionParams.isNotEmpty() -> commandArgs.actionParams[0]
            config.libraryId != null -> config.libraryId
            else -> {
                System.err.println("Error: authors requires a library ID as an argument or a saved active library.")
                System.err.println("Usage: ./abs-cli.main.kts authors [library-id]")
                exitProcess(1)
            }
        }
        val response = client.sendRequest("GET", "api/libraries/$libraryId/authors")
        val json = checkResponse(response, "authors").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val authors = json.getAsJsonArray("authors")
            val headers = listOf("ID", "Name", "Books Count")
            val rows = authors.map { a ->
                val obj = a.asJsonObject
                listOf(
                    obj.get("id").asString,
                    obj.get("name").asString,
                    obj.get("bookCount")?.asInt?.toString() ?: obj.get("numBooks")?.asInt?.toString() ?: "N/A"
                )
            }
            println("Authors:")
            printTable(headers, rows)
        }
    }
    
    "author" -> {
        if (commandArgs.actionParams.isEmpty()) {
            System.err.println("Error: author requires an author ID.")
            System.err.println("Usage: ./abs-cli.main.kts author <author-id>")
            exitProcess(1)
        }
        val authorId = commandArgs.actionParams[0]
        val response = client.sendRequest("GET", "api/authors/$authorId?include=items")
        val json = checkResponse(response, "author").asJsonObject
        
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            println("Author Details:")
            println("  ID:          $authorId")
            println("  Name:        ${json.get("name").asString}")
            println("  Description: ${json.get("description")?.asString ?: "None"}")
            
            val items = json.getAsJsonArray("libraryItems")
            if (items != null && items.size() > 0) {
                println("\nAuthor's Books:")
                val bookHeaders = listOf("Book ID", "Title", "Narrator")
                val bookRows = items.map { item ->
                    val obj = item.asJsonObject
                    val media = obj.getAsJsonObject("media")
                    val metadata = media?.getAsJsonObject("metadata")
                    listOf(
                        obj.get("id").asString,
                        metadata?.get("title")?.asString ?: "N/A",
                        metadata?.get("narratorName")?.asString ?: "N/A"
                    )
                }
                printTable(bookHeaders, bookRows)
            }
        }
    }
    
    "series" -> {
        val targetId = if (commandArgs.actionParams.isNotEmpty()) commandArgs.actionParams[0] else null
        
        if (targetId != null) {
            // First try to look up the series details from the library series list with minified=0 to get books details
            val libraryId = config.libraryId
            if (libraryId != null) {
                val libSeriesResponse = client.sendRequest("GET", "api/libraries/$libraryId/series?limit=10000&minified=0&sort=name")
                if (libSeriesResponse.statusCode() == 200) {
                    val libSeriesJson = checkResponse(libSeriesResponse, "series").asJsonObject
                    val results = libSeriesJson.getAsJsonArray("results")
                    var foundSeries: JsonObject? = null
                    for (item in results) {
                        val sObj = item.asJsonObject
                        if (sObj.get("id").asString == targetId) {
                            foundSeries = sObj
                            break
                        }
                    }
                    
                    if (foundSeries != null) {
                        if (commandArgs.json) {
                            println(gson.toJson(foundSeries))
                        } else {
                            println("Series Details:")
                            println("  ID:          $targetId")
                            println("  Name:        ${foundSeries.get("name").asString}")
                            val descElement = foundSeries.get("description")
                            val description = if (descElement == null || descElement.isJsonNull) "None" else descElement.asString
                            println("  Description: $description")
                            
                            val books = foundSeries.getAsJsonArray("books")
                            if (books != null && books.size() > 0) {
                                println("\nBooks in Series:")
                                val bookHeaders = listOf("Sequence", "Book ID", "Title", "Author")
                                val bookRows = books.map { b ->
                                    val obj = b.asJsonObject
                                    val media = obj.getAsJsonObject("media")
                                    val metadata = media?.getAsJsonObject("metadata")
                                    var sequence = metadata?.getAsJsonArray("series")?.mapNotNull { s ->
                                        val sObj = s.asJsonObject
                                        val sId = sObj.get("id")
                                        val sSeq = sObj.get("sequence")
                                        if (sId != null && !sId.isJsonNull && sId.asString == targetId) {
                                            if (sSeq != null && !sSeq.isJsonNull) sSeq.asString else null
                                        } else null
                                    }?.firstOrNull()
                                    
                                    if (sequence == null) {
                                        val sName = metadata?.get("seriesName")?.let { if (it.isJsonNull) null else it.asString }
                                        if (sName != null && sName.contains("#")) {
                                            sequence = sName.substringAfterLast("#").trim()
                                        }
                                    }
                                    if (sequence == null) sequence = "N/A"
                                    
                                    val bId = obj.get("id")?.let { if (it.isJsonNull) "N/A" else it.asString } ?: "N/A"
                                    val bTitle = metadata?.get("title")?.let { if (it.isJsonNull) "N/A" else it.asString } ?: "N/A"
                                    val bAuthor = metadata?.get("authorName")?.let { if (it.isJsonNull) "N/A" else it.asString } ?: "N/A"
                                    
                                    listOf(
                                        sequence,
                                        bId,
                                        bTitle,
                                        bAuthor
                                    )
                                }.sortedWith(compareBy<List<String>> { 
                                    it[0].toDoubleOrNull() ?: Double.MAX_VALUE 
                                }.thenBy { it[2] })
                                
                                printTable(bookHeaders, bookRows)
                            }
                        }
                        exitProcess(0)
                    }
                }
            }
            
            // Fallback: fetch from /api/series/{seriesId} which has basic info but no books array
            val response = client.sendRequest("GET", "api/series/$targetId")
            if (response.statusCode() == 200) {
                val json = checkResponse(response, "series-detail").asJsonObject
                if (commandArgs.json) {
                    println(gson.toJson(json))
                } else {
                    println("Series Details:")
                    println("  ID:          $targetId")
                    println("  Name:        ${json.get("name").asString}")
                    val descElement = json.get("description")
                    val description = if (descElement == null || descElement.isJsonNull) "None" else descElement.asString
                    println("  Description: $description")
                    println("\nBooks in Series: N/A (books details not returned by fallback endpoint)")
                }
                exitProcess(0)
            }
        }
        
        val libraryId = when {
            targetId != null -> targetId
            config.libraryId != null -> config.libraryId
            else -> {
                System.err.println("Error: series requires a library ID as an argument or a saved active library.")
                System.err.println("Usage: ./abs-cli.main.kts series [library-id]")
                exitProcess(1)
            }
        }
        val response = client.sendRequest("GET", "api/libraries/$libraryId/series?limit=10000&minified=1&sort=name")
        if (response.statusCode() == 404 && targetId != null) {
            System.err.println("Error: '$targetId' is neither a valid series ID nor library ID on this server.")
            exitProcess(2)
        }
        
        val json = checkResponse(response, "series").asJsonObject
        if (commandArgs.json) {
            println(gson.toJson(json))
        } else {
            val results = json.getAsJsonArray("results")
            val headers = listOf("ID", "Name", "Books Count")
            val rows = results.map { s ->
                val obj = s.asJsonObject
                listOf(
                    obj.get("id").asString,
                    obj.get("name").asString,
                    obj.getAsJsonArray("books")?.size()?.toString() ?: "0"
                )
            }
            println("Series:")
            printTable(headers, rows)
        }
    }
    
    else -> {
        System.err.println("Error: Unknown action '$action'")
        printHelp()
        exitProcess(1)
    }
}
