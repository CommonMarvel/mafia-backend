package org.common.marvel.mafia.component

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.config.Channel
import org.common.marvel.mafia.util.JsonUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

class MemberInfo(var account: String? = null,
                 var character: String? = null,
                 var status: String? = null)

class Protocol(var roomId: String? = null,
               var name: String? = null,
               var membersInfo: List<MemberInfo>? = null,
               var killWho: String? = null,
               var beatWho: String? = null,
               var from: String? = null,
               var msg: String? = null,
               var isPublic: Boolean? = false)

enum class Character {

    Werewolf,
    Human

}

enum class Type {

    RoomChat,
    WooChat,
    CreateRoom,
    Join,
    RequestStartGame,
    RequestSwitchDay,
    RequestSwitchNight,
    RequestKill,
    RequestBeat,
    StartGame,
    RequestDay,
    RequestNight,
    Kill,
    Beat,
    HuamnWin,
    WerewolfWin

}

class Room(val id: String,
           val members: ConcurrentLinkedQueue<SocketIOClient>,
           val werewolfMembers: ConcurrentLinkedQueue<SocketIOClient>,
           val accountSessionMap: MutableMap<String, SocketIOClient>,
           val sessionAccountMap: MutableMap<SocketIOClient, String>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private var aliveMembersCount = members.size
    var werewolfsCount = 0

    val membersInfo = mutableListOf<MemberInfo>()

    private val requestStartGameCount = AtomicInteger()
    private val requestSwitchDayCount = AtomicInteger()
    private val requestSwitchNightCount = AtomicInteger()
    private val requestKillCount = AtomicInteger()
    private val requestBeatCount = AtomicInteger()

    private val deadSessionList = ArrayList<SocketIOClient>()

    private val killVoteList = ArrayList<String>()
    private val beatVoteList = ArrayList<String>()

    fun receive(client: SocketIOClient, protocol: Protocol) {
        when (protocol.name) {
            Type.RoomChat.name -> handleRoomChat(client, protocol)
            Type.WooChat.name -> handleWooChat(client, protocol)
            Type.RequestStartGame.name -> addCheckBroadcast(requestStartGameCount, protocol)
            Type.RequestSwitchDay.name -> addCheckBroadcast(requestSwitchDayCount, protocol)
            Type.RequestSwitchNight.name -> addCheckBroadcast(requestSwitchNightCount, protocol)
            Type.RequestKill.name -> addCheckBroadcast(requestKillCount, protocol)
            Type.RequestBeat.name -> addCheckBeat(requestBeatCount, protocol)
        }
    }

    // TODO : force step

    private fun handleRoomChat(client: SocketIOClient, protocol: Protocol) {
        members.forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = protocol.roomId, name = Type.RoomChat.name, from = sessionAccountMap[client], msg = protocol.msg)))
        }
    }

    private fun handleWooChat(client: SocketIOClient, protocol: Protocol) {
        werewolfMembers.forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = protocol.roomId, name = Type.WooChat.name, from = sessionAccountMap[client], msg = protocol.msg)))
        }
    }


    private fun addCheckBroadcast(cmdCount: AtomicInteger, protocol: Protocol) {
        val addAndGet = cmdCount.addAndGet(1)

        when (protocol.name) {
            Type.RequestKill.name -> killVoteList.add(protocol.killWho!!)
        }

        if (addAndGet == aliveMembersCount) {
            cmdCount.addAndGet(-1 * aliveMembersCount)

            when (protocol.name) {
                Type.RequestStartGame.name -> processRequestStartGameCmd()
                Type.RequestSwitchDay.name -> processRequestSwitchDayCmd()
                Type.RequestSwitchNight.name -> processRequestSwitchNightCmd()
                Type.RequestKill.name -> processRequestKillCmd(members)
            }
        }
    }

    private fun addCheckBeat(cmdCount: AtomicInteger, protocol: Protocol) {
        val addAndGet = cmdCount.addAndGet(1)

        beatVoteList.add(protocol.beatWho!!)

        if (addAndGet == werewolfsCount) {
            cmdCount.addAndGet(-1 * werewolfsCount)

            processRequestBeatCmd(members)
        }
    }

    private fun processRequestStartGameCmd() {
        members.stream().forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.StartGame.name, membersInfo = membersInfo)))
            }
        }
    }

    private fun processRequestSwitchDayCmd() {
        members.stream().forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.RequestDay.name, membersInfo = membersInfo)))
            }
        }
    }

    private fun processRequestSwitchNightCmd() {
        members.stream().forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.RequestNight.name, membersInfo = membersInfo)))
            }
        }
    }

    private fun processRequestKillCmd(members: ConcurrentLinkedQueue<SocketIOClient>) {
        /* sum the vote result  */
        val killSumPairList = killVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        if (killSumPairList.size == 2 && killSumPairList[0].second == 1 && killSumPairList[1].second == 1) {
            log.info("break even, skippppp ...")

            members.stream().forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.Kill.name, membersInfo = membersInfo, killWho = "Nobody")))
            }
        } else {
            val sessionOfDead = accountSessionMap[killSumPairList[0].first]!!

            deadSessionList.add(sessionOfDead)
            aliveMembersCount -= 1
            if (werewolfMembers.contains(sessionOfDead)) {
                werewolfsCount -= 1
            }

            membersInfo.forEach {
                if (accountSessionMap[it.account]!! == sessionOfDead) {
                    it.status = "Dead"
                }
            }

            members.stream().forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.Kill.name, membersInfo = membersInfo, killWho = killSumPairList[0].first)))
            }

            killVoteList.clear()

            val aliveHumanSize = membersInfo.filter { it.character == Character.Human.name && it.status == "Alive" }.size
            if (aliveMembersCount == aliveHumanSize) {
                members.stream().forEach {
                    it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.HuamnWin.name, membersInfo = membersInfo)))
                }
            }
            val aliveWerewolfSize = membersInfo.filter { it.character == Character.Werewolf.name && it.status == "Alive" }.size
            if (aliveMembersCount == werewolfsCount && aliveMembersCount == aliveWerewolfSize) {
                members.stream().forEach {
                    it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.HuamnWin.name, membersInfo = membersInfo)))
                }
            }
        }
    }

    private fun processRequestBeatCmd(members: ConcurrentLinkedQueue<SocketIOClient>) {
        /* sum the vote result  */
        val beatSumPairList = beatVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()

        val sessionOfDead = accountSessionMap[beatSumPairList[0].first]!!

        deadSessionList.add(sessionOfDead)
        aliveMembersCount -= 1

        membersInfo.forEach {
            if (accountSessionMap[it.account]!! == sessionOfDead) {
                it.status = "Dead"
            }
        }

        members.stream().forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.Beat.name, membersInfo = membersInfo, beatWho = beatSumPairList[0].first)))
        }

        beatVoteList.clear()

        val aliveHumanSize = membersInfo.filter { it.character == Character.Human.name && it.status == "Alive" }.size
        if (aliveMembersCount == aliveHumanSize) {
            members.stream().forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.HuamnWin.name, membersInfo = membersInfo)))
            }
        }
        val aliveWerewolfSize = membersInfo.filter { it.character == Character.Werewolf.name && it.status == "Alive" }.size
        if (aliveMembersCount == werewolfsCount && aliveMembersCount == aliveWerewolfSize) {
            members.stream().forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.writeValueAsString(Protocol(roomId = id, name = Type.HuamnWin.name, membersInfo = membersInfo)))
            }
        }
    }

}
