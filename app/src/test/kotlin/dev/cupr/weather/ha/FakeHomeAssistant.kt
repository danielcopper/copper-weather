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
 * A Home Assistant on a local MockWebServer, speaking the parts the app uses: `/auth/token` and `/auth/revoke`, in the
 * forms Core 2026.11.1 answers with. Each part's answer can be set.
 */
class FakeHomeAssistant : Closeable {
    private val server = MockWebServer()

    /** The access token a refresh is answered with. */
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

    /** The requests to the HTTP endpoints, in order, with their form fields where they have a form. */
    val requests = CopyOnWriteArrayList<Pair<RecordedRequest, Map<String, String>>>()

    val url: HttpUrl
        get() = server.url("/")

    /** The fake's address; plain http, which [HaAddress.parse] would refuse. */
    val address: HaAddress
        get() = HaAddress(url)

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

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
                }
            }
        server.start()
    }

    /** Stops answering: every request after this fails to connect. */
    override fun close() = server.close()

    companion object {
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
