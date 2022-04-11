package fr.xpdustry.javelin.core

import arc.ApplicationListener
import arc.util.Log
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.JWTVerifier
import fr.xpdustry.javelin.core.model.Server
import fr.xpdustry.javelin.core.repository.ServerRepository
import fr.xpdustry.javelin.internal.JavelinConfig
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshakeBuilder
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import javax.inject.Inject

class JavelinServer @Inject constructor(
    private val config: JavelinConfig,
    private val verifier: JWTVerifier,
    private val repository: ServerRepository
): ApplicationListener {
    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        val AUTHORIZATION_REGEX = Regex("^Bearer .+$")
    }

    private val server = JavelinWebSocketServer()

    override fun init() {
        server.start()
    }

    override fun dispose() {
        server.stop(1000)
    }

    private inner class JavelinWebSocketServer : WebSocketServer(InetSocketAddress(config.port)) {

        private val servers = mutableMapOf<WebSocket, Server>()

        @Throws(InvalidDataException::class)
        override fun onWebsocketHandshakeReceivedAsServer(
            connection: WebSocket,
            draft: Draft,
            handshake: ClientHandshake
        ): ServerHandshakeBuilder {
            val builder = super.onWebsocketHandshakeReceivedAsServer(connection, draft, handshake)

            if (handshake.resourceDescriptor != "/" || !handshake.getFieldValue(AUTHORIZATION_HEADER).matches(AUTHORIZATION_REGEX)) {
                throw InvalidDataException(CloseFrame.POLICY_VALIDATION, "Invalid!")
            }

            val authorization = handshake.getFieldValue(AUTHORIZATION_HEADER)
            val token = authorization.split(' ', limit = 2)[1]

            try {
                val verified = verifier.verify(token)
                val server = repository.getServer(verified.subject)

                if (server == null || server.token != token) {
                    throw InvalidDataException(CloseFrame.POLICY_VALIDATION, "Invalid token!!")
                } else {
                    servers[connection] = server
                    return builder
                }
            } catch (e: JWTVerificationException) {
                Log.info("Verification $e $token")
                throw InvalidDataException(CloseFrame.POLICY_VALIDATION, "Unauthorized!!")
            }
        }

        override fun onStart() {
            Log.info("JAVELIN-SERVER: Opened javelin server at port @.", config.port)
        }

        override fun onOpen(connection: WebSocket, handshake: ClientHandshake) {
            Log.info("JAVELIN-SERVER: @ server has connected.", servers[connection]!!.name)
        }

        override fun onClose(connection: WebSocket, code: Int, reason: String, remote: Boolean) {
            if (!remote) {
                Log.info("JAVELIN-SERVER: @ server has unexpectedly disconnected: @", servers[connection]!!.name, reason)
            } else {
                Log.info("JAVELIN-SERVER: @ server has disconnected: @", servers[connection]!!.name, reason)
            }
            servers -= connection
        }

        override fun onMessage(connection: WebSocket, message: String) {
            Log.debug("JAVELIN-SERVER: received message from ${servers[connection]!!.name}: $message")
            connections.forEach { if (it != connection) it.send(message) }
        }

        override fun onError(connection: WebSocket?, ex: Exception) {
            if (connection == null) {
                Log.err("JAVELIN-SERVER: An exception has occurred in the javelin server.", ex)
            } else {
                Log.err("JAVELIN-SERVER: An exception has occurred in the ${servers[connection]!!.name} remote client.", ex)
            }
        }
    }
}