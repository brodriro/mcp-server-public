package im.brodriro

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import im.brodriro.entities.DefaultConfig
import im.brodriro.entities.GeocodingResponse
import im.brodriro.entities.GeocodingResult
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject


fun setup() {
    val config = DefaultConfig()
    val dotenv = Dotenv.configure().directory("/Users/brian/.env").load()

    val server = Server(
        serverInfo = Implementation(
            name = config.serverName, version = config.version
        ), options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    )

    configToolAndWeatherService(server, dotenv)

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    runBlocking {
        //getGeocoding("Trujillo, Peru, La Libertad")
        server.connect(transport)
        val done = Job()

        server.onClose {
            done.complete()
        }

        done.join()
    }
}

private fun configToolAndWeatherService(server: Server, enviroment: Dotenv) {

    server.addTool(
        name = "get_geocoding",

        description = """
        Get weather geocoding for a specific city and country
    """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("city") { put("type", "string") }
                //  putJsonObject("country") { put("country", "string") }
            },
            required = listOf("city")
        )
    ) { request ->

        val city = request.arguments["city"]?.jsonPrimitive?.content

        if (city == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'city' parameter is required.}"))
            )
        }

        val geocodingResults = getGeocoding(city)

        if (geocodingResults.size > 1) {
            val options = geocodingResults.mapIndexed { index, result ->
                buildJsonObject {
                    put("index", index)
                    put("city", result.name)
                    put("country", result.country)
                    put("admin1", result.admin1)
                    put("latitude", result.latitude)
                    put("longitude", result.longitude)
                }
            }

            val response = buildJsonObject {
                put("status", "needs_user_input")
                put("message", "Multiple cities found. Please select one:")
                putJsonArray("options") { options.forEach { add(it) } }
                putJsonObject("input_schema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("selection") {
                            put("type", "integer")
                            put("description", "Index of the selected city")
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("selection")) }
                }
            }
            CallToolResult(content = listOf(TextContent(response.toString())))
        } else if (geocodingResults.isNotEmpty()) {
            val result = geocodingResults.first()
            val response = buildJsonObject {
                put("status", "success")
                put("city", result.name)
                put("country", result.country)
                put("admin1", result.admin1)
                put("latitude", result.latitude)
                put("longitude", result.longitude)
            }
            CallToolResult(content = listOf(TextContent(response.toString())))
        } else {
            CallToolResult(content = listOf(TextContent("No cities found with that name.")))
        }
    }


    server.addTool(
        name = "get_forecast",
        description = """
        Get weather forecast for a specific city and country
    """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("latitude") { put("type", "number") }
                putJsonObject("longitude") { put("type", "number") }
            },
            required = listOf("latitude", "longitude")
        )
    ) { request ->
        val latitude = request.arguments["latitude"]?.jsonPrimitive?.doubleOrNull
        val longitude = request.arguments["longitude"]?.jsonPrimitive?.doubleOrNull
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'city' parameter is required."))
            )
        }

        val forecast = getForecast(latitude, longitude)

        CallToolResult(content = listOf(TextContent(forecast.toString())))
    }

    server.addTool(
        name = "send_email", description = "Send an email to a recipient",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("to") { put("type", "string") }
                putJsonObject("body") { put("type", "string") }
            },
            required = listOf("to", "body")
        )
    ) { request ->
        val to = request.arguments["to"]?.jsonPrimitive?.content
        val body = request.arguments["body"]?.jsonPrimitive?.content

        val resend = Resend(enviroment.get("RESEND_API_KEY"))


        val params = CreateEmailOptions.builder()
            .from("Brian <onboarding@resend.dev>")
            .to(to)
            .subject("MCP server notification")
            .html(body)
            .build()

        try {
            resend.emails().send(params)

            CallToolResult(content = listOf(TextContent("Se envió el correo correctamente.")))
        }
        catch (e: ResendException) {
            //e.printStackTrace()
            CallToolResult(content = listOf(TextContent("Ocurrió un error al enviar el correo")))
        }
    }

}

private suspend fun getForecast(latitude: Double, longitude: Double): JsonObject {
    val http = HttpClient {
        defaultRequest {
            url("https://api.open-meteo.com")
            contentType(ContentType.Application.Json)
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val response =
        http.get("/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,is_day,rain,precipitation&timezone=America%2FLos_Angeles")
            .body<JsonObject>()
    return response
}

private suspend fun getGeocoding(city: String): List<GeocodingResult> {
    val countryOrState = city.split(",").drop(1).joinToString()
    val tmpCity = if (city.contains(",")) {
        city.split(",")[0]
    } else city
    println("tmpCity: $tmpCity - countryOrState: $countryOrState")

    val http = HttpClient {
        defaultRequest {
            url("https://geocoding-api.open-meteo.com")
            contentType(ContentType.Application.Json)
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val response =
        http.get("/v1/search?name=$tmpCity&count=10&language=en&format=json")
            .body<GeocodingResponse>()

    val result = response.results.filter { result ->
        countryOrState.contains(result.country.toString()) || countryOrState.contains(result.admin1.toString())
    }

    return result
}



