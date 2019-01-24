package org.common.marvel.mafia.config

import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import org.common.marvel.mafia.service.ConnectorManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import javax.annotation.Resource

@Configuration
@ComponentScan(value = ["org.common.marvel.mafia"])
class WebSocketConfig {

    @Resource
    lateinit var connectorManager: ConnectorManager

    @Bean
    fun socketioServer(): SocketIOServer {
        val config = com.corundumstudio.socketio.Configuration()
        config.port = 9092

        val server = SocketIOServer(config)

        server.addConnectListener {
            val clientIp = it.remoteAddress
            println("""$clientIp connected !""")

            connectorManager.sessionIdClientMap.put(it.sessionId.toString(), it)

            it.sendEvent("advert_info", """[SYSTEM]: $clientIp Hello ! I'm server ! """)
            server.allClients.stream()
                    .forEach {
                        it.sendEvent("users", genUserLis(server.allClients.toList()))
                    }
        }

        server.addDisconnectListener {
            val clientIp = it.remoteAddress
            println("""$clientIp disconnected !""")

            connectorManager.sessionIdClientMap.remove(it.sessionId.toString())
            server.allClients.stream()
                    .forEach {
                        it.sendEvent("users", genUserLis(server.allClients.toList()))
                    }
        }

        server.addEventListener("login", LoginMsg::class.java) { client, data, ackSender ->
            connectorManager.accountSessionIdMap.put(data.account.orEmpty(), client.sessionId.toString())
            connectorManager.sessionIdAccountMap.put(client.sessionId.toString(), data.account.orEmpty())

            client.sendEvent("advert_info", """[SYSTEM]: ${data.account} login success ! """)
            server.allClients.stream()
                    .forEach {
                        it.sendEvent("users", genUserLis(server.allClients.toList()))
                    }
        }

        server.addEventListener("broadcast", BroadcastMsg::class.java) { client, data, ackSender ->
            if (data.content != null && data.content!!.isNotEmpty()) {
                server.allClients.stream()
                        .forEach {
                            it.sendEvent("broadcast", """[${connectorManager.sessionIdAccountMap.get(client.sessionId.toString())}]: ${data.content}""")
                        }
            }
        }

        return server
    }

    fun genUserLis(clients: List<SocketIOClient>): String {
        val stringBuilder = StringBuilder()

        clients.stream()
                .map { v -> connectorManager.sessionIdAccountMap.get(v.sessionId.toString()) }
                .filter { v -> v != null }
                .forEach { v -> stringBuilder.append("<li>$v</li>") }

        return stringBuilder.toString()
    }

}

class LoginMsg(var account: String? = null)
class BroadcastMsg(var content: String? = null)