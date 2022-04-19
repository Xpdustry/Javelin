package fr.xpdustry.javelin

import arc.ApplicationListener
import arc.util.Log
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.google.gson.GsonBuilder
import com.google.inject.ImplementedBy
import fr.xpdustry.javelin.internal.JavelinServerConfig
import fr.xpdustry.javelin.model.Server
import fr.xpdustry.javelin.model.registerEndpointTypeAdapter
import fr.xpdustry.javelin.repository.ServerRepository
import fr.xpdustry.javelin.util.fromJson
import fr.xpdustry.javelin.util.getDraft
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshakeBuilder
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(SimpleJavelinServer::class)
sealed interface JavelinServer : ApplicationListener {
    fun isConnected(server: Server): Boolean
}

@Singleton
private class SimpleJavelinServer @Inject constructor(
    private val config: JavelinServerConfig,
    private val repository: ServerRepository,
    algorithm: Algorithm
) : WebSocketServer(InetSocketAddress(config.port), config.workers, listOf(getDraft(config.https))), JavelinServer {
    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        val AUTHORIZATION_REGEX = Regex("^Bearer .+$")
    }

    private val verifier = JWT.require(algorithm).build()

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerEndpointTypeAdapter()
        .create()

    override fun init() {
        start()
    }

    override fun dispose() {
        stop(5000)
    }

    @Throws(InvalidDataException::class)
    override fun onWebsocketHandshakeReceivedAsServer(
        connection: WebSocket,
        draft: Draft,
        handshake: ClientHandshake
    ): ServerHandshakeBuilder {
        Log.debug("JAVELIN-SERVER: Receiving connection from @.", connection.remoteSocketAddress)

        val builder = super.onWebsocketHandshakeReceivedAsServer(connection, draft, handshake)
        val authorization = handshake.getFieldValue(AUTHORIZATION_HEADER)

        if (handshake.resourceDescriptor != config.path) {
            Log.debug("JAVELIN-SERVER: Rejecting connection @ (Invalid resource descriptor: @).", connection.remoteSocketAddress)
            throw InvalidDataException(CloseFrame.POLICY_VALIDATION)
        }

        if (!authorization.matches(AUTHORIZATION_REGEX)) {
            Log.debug("JAVELIN-SERVER: Rejecting connection @ (Invalid authorization header: @).", connection.remoteSocketAddress)
            throw InvalidDataException(CloseFrame.POLICY_VALIDATION)
        }

        val token = authorization.split(' ', limit = 2)[1]

        try {
            val verified = verifier.verify(token)
            val server = repository[verified.subject!!]

            if (server == null || server.token != token) {
                Log.debug("JAVELIN-SERVER: Rejecting connection @ (Invalid token).", connection.remoteSocketAddress)
                throw InvalidDataException(CloseFrame.POLICY_VALIDATION)
            }

            if (isConnected(server)) {
                Log.debug("JAVELIN-SERVER: Rejecting connection @ (Already connected).", connection.remoteSocketAddress)
                throw InvalidDataException(CloseFrame.POLICY_VALIDATION)
            }

            connection.setAttachment(server)
            return builder
        } catch (e: JWTVerificationException) {
            Log.debug("JAVELIN-SERVER: Rejecting connection @ (Invalid token).", connection.remoteSocketAddress)
            throw InvalidDataException(CloseFrame.POLICY_VALIDATION)
        }
    }

    override fun onStart() {
        Log.info("JAVELIN-SERVER: Opened javelin server at port @.", config.port)
    }

    override fun onOpen(con: WebSocket, handshake: ClientHandshake) {
        Log.info("JAVELIN-SERVER: @ server has connected.", con.server.name)
    }

    override fun onClose(con: WebSocket, code: Int, reason: String, remote: Boolean) {
        if (!remote) {
            Log.info("JAVELIN-SERVER: @ server has unexpectedly disconnected: @", con.server.name, reason)
        } else {
            Log.info("JAVELIN-SERVER: @ server has disconnected: @", con.server.name, reason)
        }
    }

    override fun onMessage(con: WebSocket, incoming: String) {
        val message = gson.fromJson<JavelinMessage>(incoming)
        Log.debug("JAVELIN-SERVER: Incoming message from ${con.server.name} > $incoming.")

        if (message.receiver == null) {
            broadcast(incoming, connections.minus(con).filter { message.endpoint in it.server.endpoints })
        } else {
            connections
                .find { it.server.name == message.receiver && message.endpoint in it.server.endpoints }
                ?.send(incoming)
        }
    }

    override fun onMessage(connection: WebSocket, message: ByteBuffer) {
        connection.close(CloseFrame.REFUSE)
    }

    override fun onError(con: WebSocket?, ex: Exception) {
        if (con == null) {
            Log.err("JAVELIN-SERVER: An exception has occurred in the javelin server.", ex)
        } else {
            Log.err("JAVELIN-SERVER: An exception has occurred in the ${con.server.name} remote client.", ex)
        }
    }

    override fun isConnected(server: Server): Boolean =
        connections.find { it.server == server } != null

    private val WebSocket.server: Server
        get() = getAttachment()
}