package io.taff.hephaestus.graphql.client

import com.taff.hephaestustest.expectation.any.satisfy
import com.taff.hephaestustest.expectation.iterable.beAnOrderedCollectionOf
import com.taff.hephaestustest.expectation.map.beAMapOf
import com.taff.hephaestustest.expectation.should
import io.taff.hephaestus.Hephaestus
import jdk.dynalink.linker.support.Guards.asType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe


object QuerySpek : Spek({

    val remoteService1 = DummyRemoteService("WritersClub", 5577)
    val remoteService2 = DummyRemoteService("fancyWritersClub", 7755)
    val remoteServices = listOf(remoteService1, remoteService2)

    beforeEachTest { remoteServices.forEach { it.start() } }

    afterEachTest { remoteServices.forEach { it.stop() } }

    describe("query") {
        val writer by memoized {  remoteService2.writers[0] }
        val otherWriter by memoized {  remoteService2.writers[1] }

        context("with a non-iterable parameter") {
            context("a query with a non-iterable argument") {
                val result by memoized {
                    Hephaestus.graphqlClients[remoteService1.name]!!.query("writer") {
                        input("name", value = remoteService1.writers.first().name)
                        select("name")
                        onAssociation("publications") {
                            onFragment("Book") { select("title", "text") }
                            onFragment("Song") { select("title", "lyrics") }
                        }
                    }.asMap()
                }

                it("successfully loads") {

                    result should satisfy { size == 2 }
                    result should beAMapOf(
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
                }

                context("with multiple services") {
                    val otherResult by memoized {
                        Hephaestus.graphqlClients[remoteService2.name]!!.query("writer") {
                            input("name", value = remoteService2.writers[1].name)
                            select("name")
                            onAssociation("publications") {
                                onFragment("Book") { select("title", "text") }
                                onFragment("Song") { select("title", "lyrics") }
                            }
                        }.asMap()
                    }

                    it("successfully loads") {
                        result should beAMapOf(
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
                        otherResult should beAMapOf(
                            "name" to otherWriter.name,
                            "publications" to otherWriter.publications.map { publication ->
                                mapOf(
                                    "title" to publication.title,
                                    when (publication) {
                                        is Publication.Book -> "text" to publication.text
                                        is Publication.Song -> "lyrics" to publication.lyrics
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        context("a query with an iterable parameter") {
            val result by memoized {
                Hephaestus.graphqlClients[remoteService1.name]!!.query("writers") {
                    input("names", value = remoteService1.writers.map { it.name })
                    select("name")
                    onAssociation("publications") {
                        onFragment("Book") { select("title", "text") }
                        onFragment("Song") { select("title", "lyrics") }
                    }
                }.asListOfMaps()
            }

            it("successfully loads") {
                result should satisfy { size == 2 }
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
                val otherRsult by memoized {
                    Hephaestus.graphqlClients[remoteService2.name]!!.query("writers") {
                        input("names", value = listOf(otherWriter.name))
                        select("name")
                        onAssociation("publications") {
                            onFragment("Book") { select("title", "text") }
                            onFragment("Song") { select("title", "lyrics") }
                        }
                    }.asListOfMaps()
                }


                it("successfully loads") {
                    result should satisfy { size == 2 }
                    result should beAnOrderedCollectionOf(
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
                    )
                    otherRsult should satisfy { size == 1 }
                    otherRsult should beAnOrderedCollectionOf(
                        mapOf(
                            "name" to otherWriter.name,
                            "publications" to otherWriter.publications.map { publication ->
                                mapOf(
                                    "title" to publication.title,
                                    when (publication) {
                                        is Publication.Book -> "text" to publication.text
                                        is Publication.Song -> "lyrics" to publication.lyrics
                                    }
                                )
                            }
                        )
                    )
                }
            }
        }
    }

    describe("asType") {
        val result by memoized {
            Hephaestus
                .graphqlClients[remoteService1.name]!!
                .query("songs") { select("title", "lyrics") }
                .asType<List<Publication.Song>>()
        }

        it("correctly parses the response") {
            result should satisfy { size == 1 }
            result should beAnOrderedCollectionOf(*remoteService1.writers[1].publications.toTypedArray())
        }
    }
})