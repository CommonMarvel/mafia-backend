package org.common.marvel.mafia.service.impl

import com.corundumstudio.socketio.SocketIOServer
import org.common.marvel.mafia.service.ConnectorService
import org.springframework.stereotype.Service
import javax.annotation.Resource

@Service
class ConnectorServiceImpl : ConnectorService {

    @Resource
    private lateinit var socketIOServer: SocketIOServer

    override fun start() {
        socketIOServer.start()
    }

}