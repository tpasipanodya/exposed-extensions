package io.taff.hephaestus.graphql.client

import org.spekframework.spek2.Spek
import io.taff.hephaestustest.expectation.any.satisfy
import io.taff.hephaestustest.expectation.iterable.beAnOrderedCollectionOf
import io.taff.hephaestustest.expectation.should
import io.taff.hephaestus.configure
import org.spekframework.spek2.style.specification.describe



class MutationSpek : Spek({

    configure { logGraphqlClientRequests = true }

    val remoteService1 = DummyRemoteService( 5577)
    val remoteService2 = DummyRemoteService( 7755)
    val services = listOf(remoteService1, remoteService2)

    beforeEachTest { services.forEach { it.start() } }

    afterEachTest { services.forEach { it.stop() } }

    describe("mutation") {
        context("With non iterable arguments") {
            val bookInput by memoized {
                Publication.Book(
                    title = "Harry Porter & The Deathly Hallows",
                    text = "The 2 men appeared out of nowhere, a few yards apart..."
                )
            }
            val result by memoized {
                remoteService1.client.mutation("addBook") {
                    input(name = "writerName", value = remoteService1.writers.first().name)
                    input(name = "book", value = bookInput)
                    select("name")
                    onAssociation("publications") {
                        onFragment("Book") { select("title", "text") }
                        onFragment("Song") { select("title", "lyrics") }
                    }
                }.resultAsListOfMaps()!!
            }

            it("successfully loads") {
                result[0]["publications"] as List<*> should satisfy { size == 2 }
                result[1]["publications"] as List<*> should satisfy { size == 1 }
                result should beAnOrderedCollectionOf(
                    *remoteService1.writers.map { writer ->
                        mapOf(
                            "name" to writer.name,
                            "publications" to writer.publications.map { publication ->
                                mapOf(
                                    "title" to publication.title,
                                    when (publication) {
                                        is Publication.Book -> "text" to publication.text
                                        is Publication.Song -> "lyrics" to publication.lyrics
                                    }
                                )
                            }
                        )
                    }.toTypedArray()
                )
            }

            context("with multiple services") {
                val otherBook by memoized {
                    Publication.Book(
                        title = "Harry Porter & Order of The Phoenix",
                        text = "The hottest day of the summer so far was drawing to a close..."
                    )
                }
                val otherResult by memoized {
                    remoteService2.client.mutation("addBook") {
                        input(name = "writerName", value = remoteService2.writers[0].name)
                        input(name = "book", value = otherBook)
                        select("name")
                        onAssociation("publications") {
                            onFragment("Book") { select("title", "text") }
                            onFragment("Song") { select("title", "lyrics") }
                        }
                    }.resultAsListOfMaps()!!
                }

                it("successfully loads") {
                    result[0]["publications"] as List<*> should satisfy { size == 2 }
                    result[1]["publications"] as List<*> should satisfy { size == 1 }
                    result should beAnOrderedCollectionOf(
                        *remoteService1.writers.map { writer ->
                            mapOf(
                                "name" to writer.name,
                                "publications" to writer.publications.map { publication ->
                                    mapOf(
                                        "title" to publication.title,
                                        when (publication) {
                                            is Publication.Book -> "text" to publication.text
                                            is Publication.Song -> "lyrics" to publication.lyrics
                                        }
                                    )
                                }
                            )
                        }.toTypedArray()!!
                    )

                    otherResult[0]["publications"] as List<*> should satisfy { size == 2 }
                    otherResult[1]["publications"] as List<*> should satisfy { size == 1 }
                    otherResult should beAnOrderedCollectionOf(
                        *remoteService2.writers.map { writer ->
                            mapOf(
                                "name" to writer.name,
                                "publications" to writer.publications.map { publication ->
                                    mapOf(
                                        "title" to publication.title,
                                        when (publication) {
                                            is Publication.Book -> "text" to publication.text
                                            is Publication.Song -> "lyrics" to publication.lyrics
                                        }
                                    )
                                }
                            )
                        }.toTypedArray()
                    )
                }
            }
        }

        context("With iterable arguments") {
            val songInputs by memoized {
                listOf(Publication.Song(
                    title = "A Star Is Born",
                    lyrics = "Everyday a star is born..."
                ))
            }
            val result by memoized {
                remoteService1.client.mutation("addSongs") {
                    input(name = "writerName", value = remoteService1.writers[1].name)
                    input(name = "songs",  value = songInputs)
                    select("name")
                    onAssociation("publications") {
                        onFragment("Book") { select("title", "text") }
                        onFragment("Song") { select("title", "lyrics") }
                    }
                }.resultAsListOfMaps()!!
            }

            it("successfully loads") {
                result[0]["publications"] as List<*> should satisfy { size == 1 }
                result[1]["publications"] as List<*> should satisfy { size == 2 }
                result should beAnOrderedCollectionOf(
                    *remoteService1.writers.map { writer ->
                        mapOf(
                            "name" to writer.name,
                            "publications" to writer.publications.map { publication ->
                                mapOf(
                                    "title" to publication.title,
                                    when (publication) {
                                        is Publication.Book -> "text" to publication.text
                                        is Publication.Song -> "lyrics" to publication.lyrics
                                    }
                                )
                            }
                        )
                    }.toTypedArray()
                )
            }

            context("with multiple services") {
                val otherSongInputs by memoized {
                    listOf(Publication.Song(
                        title = "Hard Knock Life",
                        lyrics = "It's a hard-knock life for us..."
                    ))
                }
                val otherResult by memoized {
                    remoteService2.client.mutation("addSongs") {
                        input(name = "writerName", value = remoteService2.writers[1].name)
                        input(name = "songs",  value = otherSongInputs)
                        select("name")
                        onAssociation("publications") {
                            onFragment("Book") { select("title", "text") }
                            onFragment("Song") { select("title", "lyrics") }
                        }
                    }.resultAsListOfMaps()!!
                }

                it("successfully loads") {
                    result[0]["publications"] as List<*> should satisfy { size == 1 }
                    result[1]["publications"] as List<*> should satisfy { size == 2 }
                    result should beAnOrderedCollectionOf(
                        *remoteService1.writers.map { writer ->
                            mapOf(
                                "name" to writer.name,
                                "publications" to writer.publications.map { publication ->
                                    mapOf(
                                        "title" to publication.title,
                                        when (publication) {
                                            is Publication.Book -> "text" to publication.text
                                            is Publication.Song -> "lyrics" to publication.lyrics
                                        }
                                    )
                                }
                            )
                        }.toTypedArray()
                    )

                    otherResult[0]["publications"] as List<*> should satisfy { size == 1 }
                    otherResult[1]["publications"] as List<*> should satisfy { size == 2 }
                    otherResult should beAnOrderedCollectionOf(
                        *remoteService2.writers.map { writer ->
                            mapOf(
                                "name" to writer.name,
                                "publications" to writer.publications.map { publication ->
                                    mapOf(
                                        "title" to publication.title,
                                        when (publication) {
                                            is Publication.Book -> "text" to publication.text
                                            is Publication.Song -> "lyrics" to publication.lyrics
                                        }
                                    )
                                }
                            )
                        }.toTypedArray()
                    )
                }
            }
        }
    }
})