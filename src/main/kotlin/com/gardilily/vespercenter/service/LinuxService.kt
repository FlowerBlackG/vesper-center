// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月16日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.utils.Slf4k
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter


@Service
@Slf4k
class LinuxService @Autowired constructor(

) {

    companion object {
        const val PASSWD_FILE = "/etc/passwd"
        const val BASH = "/bin/bash"
    }

    private class Shell private constructor() {
        companion object {

            private fun getProcessBuilder(cmd: String): ProcessBuilder {
                return ProcessBuilder(BASH, "-c", cmd)
                    .redirectErrorStream(true)
            }

            private fun getProcessBuilder(cmd: StringBuilder) = getProcessBuilder(cmd.toString())


            private fun Process.getIO(): Pair<BufferedReader, BufferedWriter> {
                return Pair(
                    BufferedReader(InputStreamReader(this.inputStream)),
                    BufferedWriter(OutputStreamWriter(this.outputStream))
                )
            }


            fun useradd(
                username: String,
                createHome: Boolean = true,
                skeletonDirectory: String? = null
            ): Int {
                val cmd = StringBuilder("sudo useradd $username")
                if (createHome) {
                    cmd.append(" -m")
                }

                if (skeletonDirectory != null) {
                    cmd.append(" -k '$skeletonDirectory'")
                }

                val pb = getProcessBuilder(cmd.toString())
                val process = pb.start()

                return process.waitFor()
            }


            fun userdel(
                username: String,
                force: Boolean = true
            ): Int {
                val cmd = StringBuilder("sudo userdel")
                if (force) {
                    cmd.append(" -f")
                }

                cmd.append(" $username")

                val process = getProcessBuilder(cmd.toString()).start()
                return process.waitFor()
            }


            fun passwd(
                username: String,
                password: String
            ): Int {
                val cmd = StringBuilder("sudo passwd $username")
                val process = getProcessBuilder(cmd.toString()).start()
                val (reader, writer) = process.getIO()

                writer.write("$password\n$password")

                writer.flush()
                writer.close()

                return process.waitFor()
            }


            fun rm(
                dir: String,
                recursive: Boolean = false,
                force: Boolean = false,
                sudo: Boolean = true
            ): Int {
                val cmd = StringBuilder()

                if (sudo) {
                    cmd.append("sudo")
                }
                cmd.append(" rm")

                if (recursive) {
                    cmd.append(" -r")
                }

                if (force) {
                    cmd.append(" -f")
                }

                cmd.append(" $dir")

                return getProcessBuilder(cmd).start().waitFor()
            }


            fun users(): String {
                val p = getProcessBuilder("users").start()
                val (reader, writer) = p.getIO()

                val res = StringBuilder()
                reader.readLines().forEach { res.append(it) }
                writer.close()

                return res.toString()
            }


            fun machinectlShell(
                uid: Int
            ): Int {
                val p = getProcessBuilder("sudo machinectl shell --uid=$uid").start()
                return 0
            }


            fun pkill(
                kill: Boolean,
                u: Int
            ): Int {
                val cmd = StringBuilder("sudo pkill")
                if (kill) {
                    cmd.append(" -kill")
                }
                cmd.append(" -u $u")

                return getProcessBuilder(cmd).start().waitFor()
            }


            fun free(): Pair<List<Long>, List<Long>> {
                val cmd = StringBuilder("free -b")
                val p = getProcessBuilder(cmd).start()
                val (reader, writer) = p.getIO()

                val lines = reader.readLines()
                val memLine = lines[1]
                val swapLine = lines[2]

                fun processLine(line: String): List<Long> {
                    val res = ArrayList<Long>()
                    line.replace("\r", "")
                        .replace("\n", "")
                        .split(" ")
                        .forEachIndexed { idx, it ->
                            if (idx == 0 || it.isEmpty())
                                return@forEachIndexed

                            res.add(it.toLong())
                        }

                    return res
                }

                val res = Pair(processLine(memLine), processLine(swapLine))
                writer.close()
                return res
            }

        } // companion object of private class Commands
    } // private class Commands


    fun createUser(
        username: String,
        password: String,
        skeletonDirectory: String? = null
    ): Int? {
        var resCode = Shell.useradd(username, skeletonDirectory = skeletonDirectory)
        if (resCode != 0) {
            log.error("failed to create user $username. resCode is $resCode")
            return null
        }

        // 设置密码。
        resCode = Shell.passwd(username, password)
        if (resCode != 0) {
            log.error("failed to set password for user $username. ($resCode)")
            Shell.userdel(username, force = true)
            return null
        }

        // 找找看创建的用户的 UID 是什么。
        val passwdLines = File(PASSWD_FILE).readLines()
        var uid = null as Int?
        for (it in passwdLines) {


            val segments = it.split(':')
            val name = segments[0]
            val thisUid = segments[2].toInt()
            if (name == username) {
                uid = thisUid
                break
            }
        }

        if (uid == null) {
            Shell.userdel(username, force = true)
        }
        return uid
    } // fun createUser


    fun removeUser(
        username: String
    ): Int {
        var rescode = Shell.userdel(username, force = true)

        if (rescode == 0) {
            rescode = Shell.rm("/home/$username", force = true, recursive = true, sudo = true)
        }

        return rescode
    }


    fun removeUser(seat: SeatEntity) = removeUser(seat.linuxLoginName!!)

    fun getLoggedInUsers(): Collection<String> {
        return Shell.users()
            .replace("\r", "")
            .replace("\n", "")
            .split(" ")
            .toSet()
    }


    fun getLoggedInUsersAsList(): List<String> {
        return getLoggedInUsers().toList() // toSet() 再 toList()，成功去重。
    }


    fun xdgRuntimeDirOf(uid: Int): String {
        return "/run/user/$uid"
    }

    fun xdgRuntimeDirOf(seat: SeatEntity) = xdgRuntimeDirOf(seat.linuxUid!!)


    fun isLoggedIn(linuxUsername: String): Boolean {
        return getLoggedInUsers().contains(linuxUsername)
    }

    fun isLoggedIn(seat: SeatEntity) = isLoggedIn(seat.linuxLoginName!!)


    fun loginToUser(uid: Int): Int {
        val res = Shell.machinectlShell(uid)

        return res
    }

    fun loginToUser(seat: SeatEntity) = loginToUser(seat.linuxUid!!)


    fun forceLogout(uid: Int): Int {
        return Shell.pkill(kill = true, u = uid)
    }

    fun forceLogout(seat: SeatEntity) = forceLogout(seat.linuxUid!!)


    data class SystemMemoryUsage(
        val memTotal: Long,
        val memUsed: Long,
        val memFree: Long,
        val memShared: Long,
        val memBuffOrCache: Long,
        val memAvailable: Long,
        val swapTotal: Long,
        val swapUsed: Long,
        val swapFree: Long,
    )
    fun getSystemMemoryUsage(): SystemMemoryUsage {
        val (mem, swap) = Shell.free()
        return SystemMemoryUsage(
            memTotal = mem[0],
            memUsed = mem[1],
            memFree = mem[2],
            memShared = mem[3],
            memBuffOrCache = mem[4],
            memAvailable = mem[5],
            swapTotal = swap[0],
            swapUsed = swap[1],
            swapFree = swap[2]
        )
    }
}
