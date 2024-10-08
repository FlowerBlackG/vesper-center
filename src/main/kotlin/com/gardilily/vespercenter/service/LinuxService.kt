// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月16日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.entity.UserEntity
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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@Service
@Slf4k
class LinuxService @Autowired constructor(

) {

    companion object {
        const val PASSWD_FILE = "/etc/passwd"
        const val BASH = "/bin/bash"
    }

    class Shell private constructor() {
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


            private fun Process.useIO(block: (BufferedReader, BufferedWriter) -> Unit) {

                // get io
                val (reader, writer) = this.getIO()

                // call
                block(reader, writer)

                // close
                reader.close()
                writer.close()
            }


            fun useradd(
                username: String,
                createHome: Boolean = true,
                skeletonDirectory: String? = null,
            ): Int {
                val cmd = StringBuilder("sudo useradd $username")
                    .append(" --shell $BASH")
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

            fun mv(src: String, dst: String, sudo: Boolean = false, force: Boolean = false): Int {
                val cmd = StringBuilder()
                if (sudo) {
                    cmd.append("sudo ")
                }
                cmd.append("mv")
                if (force) {
                    cmd.append(" -f")
                }

                cmd.append(" $src $dst")
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


            fun pgrep(
                u: String? = null,
                f: String? = null,
                x: Boolean = false,
                paimon: String? = null
            ): List<String> {
                val cmd = StringBuilder("pgrep")

                if (u != null) {
                    cmd.append(" -u $u")
                }

                if (f != null) {
                    cmd.append(" -f \"${f.replace("\"", "\\\"")}\"")
                }

                if (x) {
                    cmd.append(" -x")
                }

                if (paimon != null) {
                    cmd.append(" $paimon")
                }


                val p = getProcessBuilder(cmd).start()

                val processIdList = ArrayList<String>()
                p.useIO { reader, writer ->
                    reader.readLines()
                        .toList()
                        .filter { it.isNotBlank() }
                        .forEach {
                            val list = it.split(" ").filter { it.isNotBlank() }
                            processIdList += list
                        }
                }


                return processIdList
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


            fun setfacl(
                modify: String?,
                target: String,
                recursive: Boolean = true,
            ): Int {
                val cmd = StringBuilder("sudo setfacl")
                if (recursive) {
                    cmd.append(" -R")
                }

                if (modify != null) {
                    cmd.append(" -m ")
                        .append(modify)
                }

                cmd.append(' ')
                    .append(target)

                val p = getProcessBuilder(cmd).start()
                return p.waitFor()
            }

            fun whoami(): String {
                val p = getProcessBuilder("whoami").start()
                val (r, w) = p.getIO()
                val name = r.readText()
                w.close()
                return name
            }


            /**
             *
             * https://stackoverflow.com/questions/9229333/how-to-get-overall-cpu-usage-e-g-57-on-linux
             */
            fun getCpuUsage(): Double {
                val p = getProcessBuilder(
                    "cat <(grep 'cpu ' /proc/stat) <(sleep 1 && grep 'cpu ' /proc/stat) | awk -v RS=\"\" '{printf \"%f\", (\$13-\$2+\$15-\$4)*100/(\$13-\$2+\$15-\$4+\$16-\$5)}'"
                ).start()

                var res = 0.0

                p.useIO { reader, _ ->
                    res = reader.readLine().toDouble()
                }

                return res
            }


            fun echo(
                sudo: Boolean,
                txt: String,
                filePath: String?,
                append: Boolean,
            ) {
                val cmd = StringBuilder()
                if (sudo) {
                    cmd.append("sudo ")
                }
                cmd.append("echo")
                    .append(' ')
                    .append("\'${txt}\'")

                if (filePath != null) {
                    cmd.append(" |")
                    if (sudo) {
                        cmd.append(" sudo")
                    }
                    cmd.append(" tee")
                    if (append) {
                        cmd.append(" -a")
                    }
                    cmd.append(' ').append(filePath)
                }

                val p = getProcessBuilder(cmd).start()
                p.waitFor()
            }


            fun test(exp: String, sudo: Boolean): Int {
                val cmd = StringBuilder()
                if (sudo) {
                    cmd.append("sudo ")
                }
                cmd.append("test $exp")

                return getProcessBuilder(cmd).start().waitFor()
            }


            fun chown(file: String, owner: String, group: String, recursive: Boolean): Int {
                val cmd = StringBuilder()

                cmd.append("sudo ")
                cmd.append("chown")

                if (recursive) {
                    cmd.append(" -R")
                }

                cmd.append(" $owner:$group $file")
                return getProcessBuilder(cmd).start().waitFor()
            }


            fun chmod(file: String, mode: String): Int {
                val cmd = StringBuilder()
                cmd.append("sudo ")
                cmd.append("chmod")
                cmd.append(" $mode")
                cmd.append(" $file")

                return getProcessBuilder(cmd).start().waitFor()
            }

        } // companion object of private class Shell
    } // private class Shell


    private fun addSetXdgRuntimeDirToBashRC(linuxLoginName: String) {
        val fPath = "/home/$linuxLoginName/.bashrc"
        val cmd = "export XDG_RUNTIME_DIR=/run/user/\$UID"

        Shell.echo(sudo = true, append = true, txt = cmd, filePath = fPath)
    }

    private fun addLaunchVesperLauncherToBashRC(linuxLoginName: String) {

        val fPath = "/home/$linuxLoginName/.bashrc"
        val cmd = StringBuilder("vesper-launcher --daemonize")
            .append(" --domain-socket ${MacroDefines.Vesper.LAUNCHER_SOCK}")
            .append(" --quit-if-vesper-ctrl-live")
            .append(" --vesper-ctrl-sock-addr ${MacroDefines.Vesper.CONTROL_SOCK}")
            .append(" > /home/$linuxLoginName/vesper-launcher.log")

        Shell.echo(sudo = true, append = true, txt = cmd.toString(), filePath = fPath)
    }

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

        if (uid == null) {  // 创建失败
            Shell.userdel(username, force = true)
        } else {  // 创建成功
            if (skeletonDirectory == null) {
                addSetXdgRuntimeDirToBashRC(username)
                addLaunchVesperLauncherToBashRC(username)
            }
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

    fun getProcessList(
        executableName: String? = null,
        username: String? = null,
        uid: Int? = null,
        seat: SeatEntity? = null,
    ): List<String> {

        val user = if (username != null) {
            username
        } else if (seat != null) {
            seat.linuxLoginName!!
        } else if (uid != null) {
            uid.toString()
        } else
            null

        val res = Shell.pgrep(x = true, u = user, paimon = executableName)
        return res
    }

    fun loginToUser(uid: Int): Int {
        val res = Shell.machinectlShell(uid)

        return res
    }

    fun loginToUser(seat: SeatEntity) = loginToUser(seat.linuxUid!!)


    fun forceLogout(linuxUid: Int): Int {
        return Shell.pkill(kill = true, u = linuxUid)
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

    fun getSystemCpuLoad(): Double {
        return Shell.getCpuUsage()
    }


    val linuxUsername: String
        get() = System.getProperty("user.name", Shell.whoami())


    fun unlockFileAccess(path: String) {
        Shell.setfacl(
            modify = "u:$linuxUsername:rwx",
            target = path
        )
    }


    fun unlockTmpfsAccess(uid: Int) {
        unlockFileAccess(xdgRuntimeDirOf(uid))
    }

    fun unlockTmpfsAccess(seat: SeatEntity) = unlockTmpfsAccess(seat.linuxUid!!)


    fun updatePassword(seat: SeatEntity, newPassword: String): Int {
        val resCode = Shell.passwd(seat.linuxLoginName!!, newPassword)
        if (resCode != 0) {
            log.error("failed to update password for user ${seat.linuxLoginName}. ($resCode)")
        }

        return resCode
    }


    fun move(source: String, target: String, sudo: Boolean) {
        Shell.mv(src = source, dst = target, sudo = true, force = true)
    }


    fun shellTest(exp: String, sudo: Boolean): Int {
        return Shell.test(exp, sudo = sudo)
    }


    fun fixSSHPermission(seat: SeatEntity) {
        fixSSHPermission(seat.linuxLoginName!!)
    }


    fun fixSSHPermission(linuxLoginName: String) {
        val home = "/home/$linuxLoginName"
        val sshFolder = "$home/.ssh"
        val authorizedKeysFile = "$sshFolder/authorized_keys"

        Shell.chown(sshFolder, linuxLoginName, linuxLoginName, true)
        Shell.chmod(home, "700")
        Shell.chmod(sshFolder, "700")
        Shell.chmod(authorizedKeysFile, "600")
    }

}
