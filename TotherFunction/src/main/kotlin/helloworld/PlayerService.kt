package helloworld

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Data class representing a player from the API
 */
data class Player(
    val id: Int,
    val surname: String,
    val name: String,
    val patronymic: String?,
    val gotQuestionsTag: String?
)

/**
 * Data class representing a tournament from the API
 */
data class Tournament(
    val id: Int,
    val name: String,
    val dateEnd: String?,
    val dateStart: String?,
    val trueDL: Double?,
    val difficultyForecast: Double?
)

data class PlayedTournament(
    val idplayer: Int,
    val idteam: Int,
    val idtournament: Int
)

/**
 * Service class for fetching player data from the rating API
 */
class PlayerService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.rating.chgk.info"

    // Environment variables for pagination
    private val playersItemsPerPage = System.getenv("PLAYERS_ITEMS_PER_PAGE")?.toIntOrNull() ?: 30
    private val tournamentsItemsPerPage = System.getenv("TOURNAMENTS_ITEMS_PER_PAGE")?.toIntOrNull() ?: 100

    /**
     * Fetches players by surname from the API
     * @param surname The surname to search for (in Cyrillic)
     * @param name Optional name to filter results (if provided, adds name query parameter)
     * @param page The page number to fetch (default 1)
     * @param isEditor If true, filter only players with non-null gotQuestionsTag (default false)
     * @return List of players matching the surname and filters
     * @throws IOException if the request fails
     */
    fun getPlayersBySurname(surname: String, name: String? = null, page: Int = 1, isEditor: Boolean = false): List<Player> {
        val encodedSurname = URLEncoder.encode(surname, StandardCharsets.UTF_8.toString())
        var url = "$baseUrl/players?surname=$encodedSurname&itemsPerPage=$playersItemsPerPage&page=$page"

        // Add name parameter if provided
        if (!name.isNullOrBlank()) {
            val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
            url += "&name=$encodedName"
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val players = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response code: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            val listType = object : TypeToken<List<Player>>() {}.type
            gson.fromJson<List<Player>>(responseBody, listType)
        }

        // Filter by gotQuestionsTag if isEditor is true
        return if (isEditor) {
            players.filter { it.gotQuestionsTag != null }
        } else {
            players
        }
    }

    /**
     * Fetches tournaments for a specific player by player ID
     * @param playerId The ID of the player
     * @return List of tournaments the player participated in
     * @throws IOException if the request fails
     */
    fun getPlayerTournaments(playerId: Int): List<PlayedTournament> {
        val url = "$baseUrl/players/$playerId/tournaments"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response code: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            val listType = object : TypeToken<List<PlayedTournament>>() {}.type
            return gson.fromJson(responseBody, listType)
        }
    }

    /**
     * Fetches tournaments by editor ID
     * @param editorId The ID of the editor
     * @param page The page number to fetch (default 1)
     * @return List of tournaments edited by the specified editor for the given page
     * @throws IOException if the request fails
     */
    fun getTournamentsByEditor(editorId: Int, page: Int = 1): List<Tournament> {
        val url = "$baseUrl/tournaments?editor=$editorId&order[lastEditDate]=desc&itemsPerPage=$tournamentsItemsPerPage&page=$page"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response code: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            val listType = object : TypeToken<List<Tournament>>() {}.type
            gson.fromJson<List<Tournament>>(responseBody, listType)
        }
    }

    /**
     * Finds tournaments that were both edited by editorId and played by playerId
     * Returns the results sorted by dateEnd in reverse chronological order (newest first)
     *
     * @param playerId The ID of the player
     * @param editorId The ID of the editor
     * @param page The page number to fetch (default 1)
     * @return List of tournaments that match both criteria, sorted by dateEnd descending
     * @throws IOException if the request fails
     */
    fun getEditedAndPlayedTournaments(playerId: Int, editorId: Int, page: Int = 1): List<Tournament> {
        // Fetch both lists
        val editedTournaments = getTournamentsByEditor(editorId, page)
        val playedTournaments = getPlayerTournaments(playerId)

        // Create a set of played tournament IDs for efficient lookup
        val playedTournamentIds = playedTournaments.map { it.idtournament }.toSet()

        // Filter edited tournaments that were also played
        val intersection = editedTournaments.filter { it.id in playedTournamentIds }

        // Sort by dateEnd in reverse order (newest first)
        // Tournaments with null dateEnd will be placed at the end
        return intersection.sortedWith(compareByDescending(nullsLast()) { it.dateEnd })
    }

    /**
     * Formats the list of players as a JSON string
     * @param players List of players to format
     * @return JSON string representation of the players
     */
    fun formatPlayersAsJson(players: List<Player>): String {
        return gson.toJson(players)
    }

    /**
     * Formats the list of tournaments as a JSON string
     * @param tournaments List of tournaments to format
     * @return JSON string representation of the tournaments
     */
    fun formatTournamentsAsJson(tournaments: List<Tournament>): String {
        return gson.toJson(tournaments)
    }

    /**
     * Formats the list of players as a readable string
     * @param players List of players to format
     * @return Readable string representation of the players
     */
    fun formatPlayersAsString(players: List<Player>): String {
        return players.joinToString("\n") { player ->
            "ID: ${player.id}, Surname: ${player.surname}, Name: ${player.name}, Patronymic: ${player.patronymic ?: "N/A"}"
        }
    }

    /**
     * Formats the list of tournaments as a readable string
     * @param tournaments List of tournaments to format
     * @return Readable string representation of the tournaments
     */
    fun formatTournamentsAsString(tournaments: List<Tournament>): String {
        return tournaments.joinToString("\n") { tournament ->
            "ID: ${tournament.id}, Name: ${tournament.name}, End Date: ${tournament.dateEnd ?: "N/A"}"
        }
    }
}

