package org.common.marvel.mafia.component

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.config.Cmd
import org.common.marvel.mafia.util.JsonUtils
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.streams.toList

class GameProtocol(var roomId: String? = null,
                   var name: String? = null,
                   var character: String? = null,
                   var killWho: String? = null,
                   var beatWho: String? = null,
                   var msg: String? = null,
                   var nameList: List<String>? = null,
                   var charList: List<String>? = null)

enum class Type {

    RoomChat,
    WooChat,
    Join,
    FetchCharacter,
    RequestSwitchDay,
    RequestSwitchNight,
    RequestKill,
    RequestBeat,
    StartGame,
    Character,
    RequestDay,
    RequestNight,
    Kill,
    Beat

}

enum class Character {

    Werewolf,
    Human

}

class GameRoom(val id: String, val members: List<SocketIOClient>, val accountSessionMap: HashMap<String, SocketIOClient>, val sessionAccountMap: HashMap<SocketIOClient, String>) {

    private var membersCount = members.size
    private var werewolfsCount = 0

    private val fetchCharacterCount = AtomicInteger()
    private val requestSwitchDayCount = AtomicInteger()
    private val requestSwitchNightCount = AtomicInteger()
    private val requestKillCount = AtomicInteger()
    private val requestBeatCount = AtomicInteger()

    private val sessionCharacterMap = HashMap<SocketIOClient, String>()

    private val deadList = ArrayList<SocketIOClient>()
    private val werewolfMembers = ArrayList<SocketIOClient>()

    private val killVoteList = ArrayList<String>()
    private val beatVoteList = ArrayList<String>()

    private val random = Random(Date().time)

    fun receiveCmd(client: SocketIOClient, protocol: GameProtocol) {
        when (protocol.name) {
            Type.RoomChat.name -> handleRoomChat(client, protocol)
            Type.WooChat.name -> handleWooChat(client, protocol)
            Type.FetchCharacter.name -> addCheckBroadcast(client, fetchCharacterCount, protocol)
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
        werewolfMembers.stream().forEach {
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
                Type.FetchCharacter.name -> processFetchCharacterCmd(members)
                Type.RequestSwitchDay.name -> processRequestSwitchDayCmd(members)
                Type.RequestSwitchNight.name -> processRequestSwitchNightCmd(members)
                Type.RequestKill.name -> processRequestKillCmd(members)
                Type.RequestBeat.name -> processRequestBeatCmd(members)
            }
        }
    }

    private fun processFetchCharacterCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            sessionCharacterMap.put(it, Character.Human.name)
        }

        val set = HashSet<Int>()
        set.add(random.nextInt(members.size))
        // TODO : variable
        while (set.size < 2) {
            set.add(random.nextInt(members.size))
        }

        set.stream().forEach {
            sessionCharacterMap.put(members.get(it), Character.Werewolf.name)
            werewolfMembers.add(members.get(it))
        }
        werewolfsCount = werewolfMembers.size

        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Character.name, sessionCharacterMap.get(it)!!)))
        }
    }

    private fun processRequestSwitchDayCmd(members: List<SocketIOClient>) {
        val nameList = members.stream().map { v -> sessionAccountMap.get(v)!! }.toList()
        val charList = members.stream().map { v -> sessionCharacterMap.get(v)!! }.toList()

        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestDay.name, "", "", "", "", nameList, charList)))
        }
    }

    private fun processRequestSwitchNightCmd(members: List<SocketIOClient>) {
        val nameList = members.stream().map { v -> sessionAccountMap.get(v)!! }.toList()
        val charList = members.stream().map { v -> sessionCharacterMap.get(v)!! }.toList()

        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestNight.name, "", "", "", "", nameList, charList)))
        }
    }

    private fun processRequestKillCmd(members: List<SocketIOClient>) {
        val killSumPairList = killVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Kill.name, "", killSumPairList.get(0).first)))
        }

        val dead = accountSessionMap.get(killSumPairList.get(0).first)!!

        deadList.add(dead)
        membersCount--
        if (werewolfMembers.contains(dead)) {
            werewolfsCount--
        }

        killVoteList.clear()
    }

    private fun processRequestBeatCmd(members: List<SocketIOClient>) {
        val beatSumPairList = beatVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Beat.name, "", "", beatSumPairList.get(0).first)))
        }

        val dead = accountSessionMap.get(beatSumPairList.get(0).first)!!

        deadList.add(dead)
        membersCount--
        beatVoteList.clear()
    }

}
