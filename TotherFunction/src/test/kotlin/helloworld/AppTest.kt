package helloworld

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import org.junit.Assert.*
import org.junit.Test

class AppTest {
    @Test
    fun testMissingParameter() {
        val app = App()
        val result = app.handleRequest(null, null)
        assertEquals(400, result.statusCode.toInt())
        assertEquals("application/json", result.headers["Content-Type"])
        val content = result.body
        assertNotNull(content)
        assertTrue(content.contains("error"))
        assertTrue(content.contains("Missing required parameters"))
    }

    @Test
    fun testWithSurnameParameter() {
        val app = App()
        val input = APIGatewayProxyRequestEvent()
        input.queryStringParameters = mapOf("surname" to "Бороненко")

        val result = app.handleRequest(input, null)
        assertEquals("application/json", result.headers["Content-Type"])
        val content = result.body
        assertNotNull(content)
        // Response should be a JSON array
        assertTrue(content.trim().startsWith("["))
    }

    @Test
    fun testWithBothPlayerIdAndEditorId() {
        val app = App()
        val input = APIGatewayProxyRequestEvent()
        input.queryStringParameters = mapOf("playerId" to "30236", "editorId" to "27009")

        val result = app.handleRequest(input, null)
        assertEquals("application/json", result.headers["Content-Type"])
        val content = result.body
        assertNotNull(content)
        // Response should be a JSON array
        assertTrue(content.trim().startsWith("["))
    }

    @Test
    fun testWithInvalidPlayerId() {
        val app = App()
        val input = APIGatewayProxyRequestEvent()
        input.queryStringParameters = mapOf("playerId" to "invalid", "editorId" to "51")

        val result = app.handleRequest(input, null)
        assertEquals(400, result.statusCode.toInt())
        assertEquals("application/json", result.headers["Content-Type"])
        val content = result.body
        assertNotNull(content)
        assertTrue(content.contains("error"))
        assertTrue(content.contains("valid integer"))
    }

    @Test
    fun testWithInvalidEditorId() {
        val app = App()
        val input = APIGatewayProxyRequestEvent()
        input.queryStringParameters = mapOf("playerId" to "51", "editorId" to "abc")

        val result = app.handleRequest(input, null)
        assertEquals(400, result.statusCode.toInt())
        assertEquals("application/json", result.headers["Content-Type"])
        val content = result.body
        assertNotNull(content)
        assertTrue(content.contains("error"))
        assertTrue(content.contains("valid integer"))
    }

    @Test
    fun testWithOnlyPlayerId() {
        val app = App()
        val input = APIGatewayProxyRequestEvent()
        input.queryStringParameters = mapOf("playerId" to "51")

        val result = app.handleRequest(input, null)
        assertEquals(400, result.statusCode.toInt())
        assertEquals("application/json", result.headers["Content-Type"])
        val content = result.body
        assertNotNull(content)
        assertTrue(content.contains("error"))
        assertTrue(content.contains("Missing required parameters"))
    }
}
