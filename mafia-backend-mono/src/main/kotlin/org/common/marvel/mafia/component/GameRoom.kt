package org.common.marvel.mafia.component

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.config.Cmd
import org.common.marvel.mafia.util.JsonUtils
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashSet
import kotlin.streams.toList

data class GameProtocol(val roomId: String, val name: String, var character: String, var killWho: String, var beatWho: String)

enum class Type {

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

class GameRoom(val id: String, val members: List<SocketIOClient>) {

    val membersCount = members.size

    val fetchCharacterCount = AtomicInteger()
    val requestSwitchDayCount = AtomicInteger()
    val requestSwitchNightCount = AtomicInteger()
    val requestKillCount = AtomicInteger()
    val requestBeatCount = AtomicInteger()

    val sessionIdCharacterMap = HashMap<String, String>()

    val killSum = ArrayList<String>()
    val beatSum = ArrayList<String>()

    val random = Random(Date().time)

    fun receiveCmd(protocol: GameProtocol) {
        when (protocol.name) {
            Type.FetchCharacter.name -> addCheckBroadcast(fetchCharacterCount, protocol)
            Type.RequestSwitchDay.name -> addCheckBroadcast(requestSwitchDayCount, protocol)
            Type.RequestSwitchNight.name -> addCheckBroadcast(requestSwitchNightCount, protocol)
            Type.RequestKill.name -> addCheckBroadcast(requestKillCount, protocol)
            Type.RequestBeat.name -> addCheckBroadcast(requestBeatCount, protocol)
            // else TODO
        }
    }

    fun addCheckBroadcast(cmdCount: AtomicInteger, protocol: GameProtocol) {
        val addAndGet = cmdCount.addAndGet(1)

        when (protocol.name) {
            Type.RequestKill.name -> killSum.add(protocol.name)
            Type.RequestBeat.name -> beatSum.add(protocol.name)
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

    fun processFetchCharacterCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            sessionIdCharacterMap.put(it.sessionId.toString(), Character.Human.name)
        }

        val set = HashSet<Int>()
        set.add(random.nextInt(members.size))
        while (set.size < 2) {
            set.add(random.nextInt(members.size))
        }

        set.stream().forEach {
            sessionIdCharacterMap.put(members.get(it).sessionId.toString(), Character.Werewolf.name)
        }

        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Character.name, sessionIdCharacterMap.get(it.sessionId.toString())!!, "", "")))
        }
    }

    fun processRequestSwitchDayCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestDay.name, "", "", "")))
        }
    }

    fun processRequestSwitchNightCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestNight.name, "", "", "")))
        }
    }

    fun processRequestKillCmd(members: List<SocketIOClient>) {
        val killSumPairList = killSum.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o1.second - o2.second }.toList()
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Kill.name, "", killSumPairList.get(0).first, "")))
        }
    }

    fun processRequestBeatCmd(members: List<SocketIOClient>) {
        val beatSumPairList = beatSum.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o1.second - o2.second }.toList()
        members.stream().forEach {
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Beat.name, "", "", beatSumPairList.get(0).first)))
        }
    }

}
