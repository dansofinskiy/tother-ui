package helloworld

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.gson.Gson
import java.io.IOException

/**
 * Handler for requests to Lambda function.
 */
class App : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val playerService = PlayerService()
    private val gson = Gson()

    override fun handleRequest(input: APIGatewayProxyRequestEvent?, context: Context?): APIGatewayProxyResponseEvent {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Headers" to "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token",
            "Access-Control-Allow-Methods" to "GET,POST,OPTIONS"
        )

        val response = APIGatewayProxyResponseEvent().withHeaders(headers)

        // Handle CORS preflight
        if (input?.httpMethod?.uppercase() == "OPTIONS") {
            return response.withStatusCode(200).withBody("{}")
        }

        return try {
            val queryParams = input?.queryStringParameters ?: emptyMap()

            // Check which type of request this is
            when {
                // Type 2: Both playerId and editorId provided - find intersection of tournaments
                queryParams.containsKey("playerId") && queryParams.containsKey("editorId") -> {
                    val playerIdStr = queryParams["playerId"]
                    val editorIdStr = queryParams["editorId"]

                    if (playerIdStr.isNullOrBlank() || editorIdStr.isNullOrBlank()) {
                        return response
                            .withStatusCode(400)
                            .withBody(gson.toJson(mapOf("error" to "Both playerId and editorId parameters are required and cannot be empty")))
                    }

                    val playerId = playerIdStr.toIntOrNull()
                        ?: return response
                            .withStatusCode(400)
                            .withBody(gson.toJson(mapOf("error" to "playerId must be a valid integer")))

                    val editorId = editorIdStr.toIntOrNull()
                        ?: return response
                            .withStatusCode(400)
                            .withBody(gson.toJson(mapOf("error" to "editorId must be a valid integer")))

                    // Get intersection of edited and played tournaments, sorted by date
                    val tournaments = playerService.getEditedAndPlayedTournaments(playerId, editorId)
                    val output = playerService.formatTournamentsAsJson(tournaments)

                    response
                        .withStatusCode(200)
                        .withBody(output)
                }

                // Type 1: Find players by surname
                queryParams.containsKey("surname") -> {
                    val surname = queryParams["surname"]

                    if (surname.isNullOrBlank()) {
                        return response
                            .withStatusCode(400)
                            .withBody(gson.toJson(mapOf("error" to "surname parameter cannot be empty")))
                    }

                    // Fetch players by surname
                    val players = playerService.getPlayersBySurname(surname)
                    val output = playerService.formatPlayersAsJson(players)

                    response
                        .withStatusCode(200)
                        .withBody(output)
                }

                else -> {
                    response
                        .withStatusCode(400)
                        .withBody(gson.toJson(mapOf(
                            "error" to "Missing required parameters. Use 'surname' OR both 'playerId' and 'editorId'"
                        )))
                }
            }
        } catch (e: IOException) {
            context?.logger?.log("Error: ${e.message}")
            response
                .withBody(gson.toJson(mapOf("error" to "Failed to fetch data: ${e.message}")))
                .withStatusCode(500)
        } catch (e: NumberFormatException) {
            context?.logger?.log("Number format error: ${e.message}")
            response
                .withBody(gson.toJson(mapOf("error" to "Invalid number format: ${e.message}")))
                .withStatusCode(400)
        } catch (e: Exception) {
            context?.logger?.log("Unexpected error: ${e.message}")
            response
                .withBody(gson.toJson(mapOf("error" to "Internal server error: ${e.message}")))
                .withStatusCode(500)
        }
    }
}

