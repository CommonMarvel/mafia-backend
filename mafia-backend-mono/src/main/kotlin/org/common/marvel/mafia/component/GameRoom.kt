package org.common.marvel.mafia.component

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.config.Cmd
import org.common.marvel.mafia.service.GameProtocol
import org.common.marvel.mafia.service.MemberInfo
import org.common.marvel.mafia.util.JsonUtils
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.streams.toList

//enum class RoomStatus {
//
//    Start,
//    Day,
//    Night
//
//}

enum class Type {

    RoomChat,
    WooChat,
    MembersInfo,
    Join,
    RequestSwitchDay,
    RequestSwitchNight,
    RequestKill,
    RequestBeat,
    StartGame,
    RequestDay,
    RequestNight,
    Kill,
    Beat

}

class GameRoom(val id: String,
               val members: List<SocketIOClient>,
               val accountSessionMap: HashMap<String, SocketIOClient>,
               val sessionAccountMap: HashMap<SocketIOClient, String>,
               val membersInfo: List<MemberInfo>) {

    private var membersCount = members.size
    private var werewolfsCount = 0

    private val requestSwitchDayCount = AtomicInteger()
    private val requestSwitchNightCount = AtomicInteger()
    private val requestKillCount = AtomicInteger()
    private val requestBeatCount = AtomicInteger()

    private val deadSessionList = ArrayList<SocketIOClient>()
    private val werewolfSessionMembers = ArrayList<SocketIOClient>()

    private val killVoteList = ArrayList<String>()
    private val beatVoteList = ArrayList<String>()

    fun receiveCmd(client: SocketIOClient, protocol: GameProtocol) {
        when (protocol.name) {
            Type.RoomChat.name -> handleRoomChat(client, protocol)
            Type.WooChat.name -> handleWooChat(client, protocol)
            Type.RequestSwitchDay.name -> addCheckBroadcast(client, requestSwitchDayCount, protocol)
            Type.RequestSwitchNight.name -> addCheckBroadcast(client, requestSwitchNightCount, protocol)
            Type.RequestKill.name -> addCheckBroadcast(client, requestKillCount, protocol)
            Type.RequestBeat.name -> addCheckBroadcast(client, requestBeatCount, protocol)
            // else TODO
        }
    }

    private fun handleRoomChat(client: SocketIOClient, protocol: GameProtocol) {
        members.stream().forEach {
            it.sendEvent(Cmd.Broadcast.name, """[${sessionAccountMap.get(client)}]: ${protocol.msg}""")
        }
    }

    private fun handleWooChat(client: SocketIOClient, protocol: GameProtocol) {
        werewolfSessionMembers.stream().forEach {
            it.sendEvent(Cmd.Broadcast.name, """[${sessionAccountMap.get(client)}]: ${protocol.msg}""")
        }
    }

    private fun addCheckBroadcast(client: SocketIOClient, cmdCount: AtomicInteger, protocol: GameProtocol) {
        val addAndGet = cmdCount.addAndGet(1)

        when (protocol.name) {
            Type.RequestKill.name -> killVoteList.add(protocol.killWho!!)
            Type.RequestBeat.name -> beatVoteList.add(protocol.beatWho!!)
        }

        if (addAndGet.equals(membersCount)) {
            cmdCount.addAndGet(-1 * membersCount)

            when (protocol.name) {
                Type.RequestSwitchDay.name -> processRequestSwitchDayCmd(members)
                Type.RequestSwitchNight.name -> processRequestSwitchNightCmd(members)
                Type.RequestKill.name -> processRequestKillCmd(members)
                Type.RequestBeat.name -> processRequestBeatCmd(members)
            }
        }
    }

    private fun processRequestSwitchDayCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestDay.name, membersInfo)))
        }
    }

    private fun processRequestSwitchNightCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestNight.name, membersInfo)))
        }
    }

    private fun processRequestKillCmd(members: List<SocketIOClient>) {
        /* sum the vote result  */
        val killSumPairList = killVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Kill.name, membersInfo, killSumPairList.get(0).first)))
        }

        val sessionOfDead = accountSessionMap.get(killSumPairList.get(0).first)!!

        deadSessionList.add(sessionOfDead)
        membersCount--
        if (werewolfSessionMembers.contains(sessionOfDead)) {
            werewolfsCount--
        }

        killVoteList.clear()
    }

    private fun processRequestBeatCmd(members: List<SocketIOClient>) {
        /* sum the vote result  */
        val beatSumPairList = beatVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Beat.name, membersInfo, "", beatSumPairList.get(0).first)))
        }

        val sessionOfDead = accountSessionMap.get(beatSumPairList.get(0).first)!!

        deadSessionList.add(sessionOfDead)
        membersCount--

        beatVoteList.clear()
    }

}
