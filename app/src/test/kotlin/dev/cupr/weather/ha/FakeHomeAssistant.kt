package dev.cupr.weather.ha

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import java.io.Closeable
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * A Home Assistant on a local MockWebServer, speaking the parts the app uses: `/auth/token`, `/auth/revoke` and
 * `/api/states/sensor.copper_weather`, in the forms Core 2026.11.1 answers with. Each part's answer can be set.
 */
class FakeHomeAssistant : Closeable {
    private val server = MockWebServer()

    /** The access token a refresh is answered with, and the one the states accept. */
    var accessToken = "access-1"

    /**
     * The challenge the login page was given, as `/auth/authorize` would keep it: when set, the code is traded only for
     * a `code_verifier` whose SHA-256, base64url without padding, is this challenge, as Core checks it
     * (`_verify_code_verifier`).
     */
    var challenge: String? = null

    /** When set, a request to `/auth/token` waits for it, after counting down [tokenAsked]. */
    var tokenGate: CountDownLatch? = null

    val tokenAsked = CountDownLatch(1)

    /** The answer to `/auth/token`, by the request's form fields. */
    var token: (Map<String, String>) -> MockResponse = { form ->
        when {
            form["grant_type"] != "authorization_code" -> {
                json("""{"access_token":"$accessToken","token_type":"Bearer","expires_in":1800}""")
            }

            challenge != null && s256(form["code_verifier"].orEmpty()) != challenge -> {
                json("""{"error":"invalid_grant","error_description":"Invalid code verifier"}""", 400)
            }

            else -> {
                json(
                    """{"access_token":"$accessToken","token_type":"Bearer",""" +
                        """"refresh_token":"refresh-1","expires_in":1800}""",
                )
            }
        }
    }

    /** The answer to `/auth/revoke`; Core answers 200 for any token. */
    var revoke: () -> MockResponse = { MockResponse.Builder().code(200).build() }

    /** The app sensor's attribute `data`, which [states] answers with by default. */
    var data = DATA

    /** The app sensor's state as JSON, or another answer to `/api/states/sensor.copper_weather`. */
    var states: () -> MockResponse = {
        json(
            """{"entity_id":"sensor.copper_weather","state":"2026-11-20T08:00:00+00:00","attributes":{"data":$data}}""",
        )
    }

    /** When set, a request for the states waits for it, after counting down [statesAsked]. */
    var statesGate: CountDownLatch? = null

    val statesAsked = CountDownLatch(1)

    /** The requests to the HTTP endpoints, in order, with their form fields where they have a form. */
    val requests = CopyOnWriteArrayList<Pair<RecordedRequest, Map<String, String>>>()

    val url: HttpUrl
        get() = server.url("/")

    /** The fake's address; plain http, which [HaAddress.parse] would refuse. */
    val address: HaAddress
        get() = checkNotNull(HaAddress.stored(url.toString()))

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val form = form(request)
                    requests += request to form
                    return when (request.url.encodedPath) {
                        "/auth/token" -> {
                            tokenAsked.countDown()
                            tokenGate?.await()
                            token(form)
                        }

                        "/auth/revoke" -> {
                            revoke()
                        }

                        "/api/states/sensor.copper_weather" -> {
                            states(request)
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
                }
            }
        server.start()
    }

    private fun states(request: RecordedRequest): MockResponse {
        statesAsked.countDown()
        statesGate?.await()
        return if (request.headers["Authorization"] == "Bearer $accessToken") {
            states()
        } else {
            json("""{"message":"Unauthorized"}""", 401)
        }
    }

    /** Stops answering: every request after this fails to connect. */
    override fun close() = server.close()

    companion object {
        /** The app sensor's `data` in schema version 1, with neutral values; `rain_at` and `storm` are absent. */
        const val DATA =
            """{"schema_version":1,"attribution":"Weather data: a test source",""" +
                """"now":{"condition":"partlycloudy","temperature":7.4,"humidity":81,"wind_speed":12.2},""" +
                """"twilight":"day","hours":[],"days":[],"warnings":[],"pollen_max":0.5}"""

        fun json(
            body: String,
            code: Int = 200,
        ): MockResponse =
            MockResponse
                .Builder()
                .code(code)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build()

        /** RFC 7636's S256, written out here so the test does not check the app with the app's own code. */
        fun s256(verifier: String): String =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        private fun form(request: RecordedRequest): Map<String, String> {
            val body = request.body?.utf8().orEmpty()
            if (request.headers["Content-Type"]?.startsWith("application/x-www-form-urlencoded") != true) {
                return emptyMap()
            }
            return HttpUrl
                .Builder()
                .scheme("http")
                .host("form")
                .encodedQuery(body)
                .build()
                .let { url -> url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() } }
        }
    }
}
