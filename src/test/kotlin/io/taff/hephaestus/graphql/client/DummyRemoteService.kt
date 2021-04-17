package io.taff.hephaestus.graphql.client

import com.apurebase.kgraphql.KGraphQL
import io.javalin.Javalin
import io.taff.hephaestus.Config
import io.taff.hephaestus.configure
import kotlinx.coroutines.runBlocking
import io.javalin.http.Context

/** A writer can have multiple publications */
data class Writer(val name: String, val publications: MutableList<Publication>)

/** Publications have diffrerent characteristics but share a title */
sealed class Publication(open val title: String) {
    data class Book(override val title: String, val text: String) : Publication(title)
    data class Song(override val title: String, val lyrics: String) : Publication(title)
}

class DummyService(val name: String, val port: Int) {

    /* The data being served. */
    val writers = mutableListOf<Writer>()

    /** The graphql schema */
    val schema = KGraphQL.schema {
        query("writer") {
            resolver { name: String ->
                writers.first { it.name == name }
            }
        }

        query("writers") {
            resolver { names: List<String> ->
                writers.filter { it.name in names }
            }
        }

        mutation("addWriter") {
            resolver { writer: Writer ->
                writers.add(writer)
                writers
            }
        }

        mutation("addSongs") {
            resolver { writerName: String,
                       songs: List<Publication.Song> ->
                writers.first { it.name == writerName }
                    .also { it.publications.addAll(songs) }
                writers
            }
        }

        type<Writer>()

        unionType<Publication>{
            type<Publication.Book>()
            type<Publication.Song>()
        }
    }

    /** The Javalin instance */
    private var app: Javalin? = null

    /** Point the graphql client to this dummy service */
    private val hephaestusConfig = configure {
        services.service(ServiceConfig(
            name,
            "http://localhost:$port/graphql"
        ))
    }

    /** Start the service */
    fun start() {
        resetWriters()
        resetJavalin()
    }


    /** stop the service */
    fun stop() {
        app?.stop()
    }

    private fun resetWriters() {
        writers.clear()

        writers.add(Writer("J,K Rowling",
            mutableListOf(Publication.Book("Prisoner of Azkaban",
                "Happiness can be found, even in the darkest of times, if one only remembers to turn on the light.\n..."))))

        writers.add(Writer("Jay. Z",
            mutableListOf(Publication.Song("Blueprint (2)",
                "I will not lose, for even in defeat, there's a valuable lesson learned, so it evens up for me..."))))
    }

    private fun resetJavalin() {
        app = Javalin.create()
        app?.start(port)
        app?.post("/graphql") { ctx: Context ->
            val  body = Config.objectMapper.readTree(ctx.body())
            runBlocking {
                schema.execute(
                    body["query"].asText(),
                    body["variables"].toPrettyString()
                )
            }.let { ctx.result(it) }
        }
    }
}