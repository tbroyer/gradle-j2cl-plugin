package net.ltgt.gradle.j2cl.internal

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files

internal fun createSimpleHttpServer(rootDir: File): HttpServer {
    val server = HttpServer.create()
    server.createContext("/") {
        it.useToRun {
            when (requestMethod) {
                "HEAD", "GET" -> {
                    val f = rootDir.resolve("." + requestURI.path)
                    if (!f.path.startsWith(rootDir.path)) {
                        sendResponseHeaders(404, -1)
                        return@useToRun
                    }
                    // XXX: handle directories? (redirect if missing trailing slash, return index.html otherwise)
                    if (!f.isFile) {
                        sendResponseHeaders(404, -1)
                        return@useToRun
                    }
                    responseHeaders["Content-Type"] = guessContentType(f)
                    if (requestMethod == "HEAD") {
                        responseHeaders["Content-Length"] = f.length().toString()
                        sendResponseHeaders(200, -1)
                    } else {
                        sendResponseHeaders(200, f.length())
                        Files.copy(f.toPath(), responseBody)
                    }
                }
                else -> {
                    responseHeaders["Allow"] = "GET,HEAD"
                    sendResponseHeaders(405, -1)
                }
            }
        }
    }
    return server
}

internal val HttpServer.baseUrl: String
    get() = "http://${address.hostString}:${address.port}"

internal fun HttpServer.url(path: String): String = "$baseUrl$path".also {
    assert(it.startsWith('/')) { "Path must start with a slash" }
}

internal inline fun <R> HttpServer.use(crossinline block: (HttpServer) -> R) =
    use(
        {
            bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            start()
            block(this)
        },
        {
            stop(0)
        }
    )

// Unfortunately, HttpExchange implements AutoCloseable only since JDK 14: https://bugs.openjdk.java.net/browse/JDK-8229235
internal fun HttpExchange.useToRun(action: HttpExchange.() -> Unit) =
    use(
        {
            action(this)
        },
        {
            close()
        }
    )

internal fun <T> T.use(block: T.() -> Unit, close: T.() -> Unit) {
    var exception: Throwable? = null
    try {
        block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when (exception) {
            null -> close(this)
            else -> try { close(this) } catch (e: Throwable) { exception.addSuppressed(e) }
        }
    }
}

private fun guessContentType(path: File): String {
    // From rules_closure's phantomjs_harness, except for the fallback
    // Copyright 2016 The Closure Rules Authors. All rights reserved.
    return when (path.extension) {
        "js" -> "application/javascript;charset=utf-8"
        "html" -> "text/html;charset=utf-8"
        "css" -> "text/css;charset=utf-8"
        "txt" -> "text/plain;charset=utf-8"
        "xml" -> "application/xml;charset=utf-8"
        "gif" -> "image/gif"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> Files.probeContentType(path.toPath()) ?: "application/octet-stream"
    }
}
