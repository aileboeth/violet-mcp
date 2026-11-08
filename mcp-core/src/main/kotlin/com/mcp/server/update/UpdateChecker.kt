package com.mcp.server.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 发布来源（App 测试时可以换成假的） */
interface ReleaseSource {
    fun latest(): UpdateChecker.Release
    fun download(release: UpdateChecker.Release, target: File, progress: (Long, Long) -> Unit = { _, _ -> }): String
}

/**
 * 应用自身的更新检查与下载（Violet MCP 1.4.0；1.4.4 起可经 GitHub 加速站）。纯 JVM，不依赖 Android。
 *
 * - 最新版本从发布自带的 `SHA256SUMS.txt` 读：`<来源前缀>https://github.com/<repo>/releases/latest/download/
 *   SHA256SUMS.txt`（1.4.4 起不再用 GitHub API：加速站不转发 API，API 每小时还限 60 次）。里面版本号最高的
 *   `violet-mcp-<版本>-release.apk` 就是要装的，它的校验值也在这个文件里。
 * - [sources] 按顺序试（[Sources]：GitHub、内置加速站、自定义地址），前一个失败就试下一个，全部失败时
 *   列出每个来源的原因。
 * - 每一次请求（包括跳转）都只允许 https 和 GitHub 的主机 + 当前来源的主机，在连接之前检查；安装包大小有上限。
 * - 下载的文件在 SHA-256 与 SHA256SUMS.txt 一致后才保留，否则删除并报错。
 *
 * 安装前的包名、签名证书、版本号检查在 App 侧完成（需要 PackageManager）——加速站改了安装包也装不上。
 *
 * 单独放在 com.mcp.server.update 包里：协议核心 com.mcp.server.core 和工具包仍然不允许任何写文件操作
 * （tests/verify_apk.py 检查），只有这里和 AppUpdater 可以写、删自己缓存目录里的安装包。
 */
class UpdateChecker(
    private val repo: String = DEFAULT_REPO,
    private val sources: List<Source> = listOf(Sources.GITHUB_SOURCE),
    private val githubBase: String = "https://github.com",
    private val allowedHosts: Set<String> = GITHUB_HOSTS,
    private val requireHttps: Boolean = true,
) : ReleaseSource {
    companion object {
        const val DEFAULT_REPO = "aileboeth/violet-mcp"
        val GITHUB_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
        const val MAX_APK_BYTES = 64L * 1024 * 1024
        private const val MAX_SUMS_BYTES = 64L * 1024
        private val APK = Regex("^violet-mcp-(\\d+\\.\\d+\\.\\d+)-release\\.apk$")
    }

    class UpdateException(message: String) : IOException(message)

    data class Release(
        val tag: String,
        val version: String,
        val pageUrl: String?,
        val publishedAt: String?,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val apkSize: Long,
        val sumsUrl: String,
        val sha256: String? = null,
        val source: Source = Sources.GITHUB_SOURCE,
    )

    // redirects are followed by hand (open): every hop is checked BEFORE anything connects to it
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun hostsFor(source: Source): Set<String> = source.host?.let { allowedHosts + it } ?: allowedHosts

    private fun checkUrl(url: HttpUrl, hosts: Set<String>) {
        if ((requireHttps && !url.isHttps) || url.host !in hosts) {
            throw UpdateException("拒绝连接 ${url.host}：更新只从 GitHub 或你选的加速站下载")
        }
    }

    /** GET [url], following at most 5 redirects, each one inside [hosts]. The caller closes the response. */
    private fun open(url: String, hosts: Set<String>, accept: String? = null): Response {
        var current = url.toHttpUrlOrNull() ?: throw UpdateException("地址不正确")
        repeat(6) {
            checkUrl(current, hosts)
            val request = Request.Builder().url(current).header("User-Agent", "violet-mcp-updater")
                .apply { if (accept != null) header("Accept", accept) }.build()
            val resp = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw UpdateException("网络不通或被拦截（${e.javaClass.simpleName}）")
            }
            if (resp.code !in 300..399) return resp
            val location = resp.header("Location")
            resp.close()
            current = location?.let { current.resolve(it) } ?: throw UpdateException("下载地址跳转不正确")
        }
        throw UpdateException("下载地址跳转次数太多")
    }

    /** 最新发布：按顺序试每个来源。全部失败时报错，列出每个来源的原因（中文） */
    override fun latest(): Release {
        val reasons = mutableListOf<String>()
        for (source in sources.ifEmpty { listOf(Sources.GITHUB_SOURCE) }) {
            try {
                return latestFrom(source)
            } catch (e: UpdateException) {
                reasons += "${source.label}：${e.message}"
            }
        }
        throw UpdateException(reasons.joinToString("；"))
    }

    private fun latestFrom(source: Source): Release {
        val text = String(get(source.url("$githubBase/$repo/releases/latest/download/SHA256SUMS.txt"),
            MAX_SUMS_BYTES, hostsFor(source)), Charsets.UTF_8)
        val sums = parseSums(text)
        if (sums.isEmpty()) throw UpdateException("SHA256SUMS.txt 的内容不对，不更新")
        val found = sums.keys.mapNotNull { name ->
            APK.find(name)?.groupValues?.get(1)?.let { v -> Versions.key(v)?.let { key -> Triple(name, v, key) } }
        }.maxWithOrNull(compareBy({ it.third.first }, { it.third.second }, { it.third.third }))
            ?: throw UpdateException("最新发布里没有安装包（violet-mcp-<版本>-release.apk）")
        val (apkName, version, _) = found
        val tag = "v$version"
        val download = "$githubBase/$repo/releases/download/$tag"
        return Release(
            tag = tag, version = version, pageUrl = "https://github.com/$repo/releases/tag/$tag",
            publishedAt = null, notes = "", apkName = apkName, apkUrl = source.url("$download/$apkName"),
            apkSize = -1, sumsUrl = source.url("$download/SHA256SUMS.txt"), sha256 = sums[apkName], source = source,
        )
    }

    /** SHA256SUMS.txt 里安装包的 SHA-256（小写十六进制） */
    fun expectedSha256(release: Release): String {
        release.sha256?.let { return it }
        val text = String(get(release.sumsUrl, MAX_SUMS_BYTES, hostsFor(release.source)), Charsets.UTF_8)
        return parseSums(text)[release.apkName]
            ?: throw UpdateException("SHA256SUMS.txt 里没有 ${release.apkName}，不更新")
    }

    /**
     * 下载安装包到 [target]，SHA-256 与 SHA256SUMS.txt 一致才保留。返回校验值。
     * [progress] 收到已下载字节数和总字节数（未知时为 -1）。
     */
    override fun download(release: Release, target: File, progress: (Long, Long) -> Unit): String {
        if (release.apkSize > MAX_APK_BYTES) throw UpdateException("安装包太大（${release.apkSize} 字节），不下载")
        val expected = expectedSha256(release)
        val tmp = File(target.parentFile, target.name + ".part")
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            open(release.apkUrl, hostsFor(release.source)).use { resp ->
                    if (!resp.isSuccessful) throw UpdateException("下载安装包失败：HTTP ${resp.code}")
                    val body = resp.body ?: throw UpdateException("下载安装包失败：没有内容")
                    val total = body.contentLength()
                    if (total > MAX_APK_BYTES) throw UpdateException("安装包太大，不下载")
                    var read = 0L
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                read += n
                                if (read > MAX_APK_BYTES) throw UpdateException("安装包太大，不下载")
                                digest.update(buf, 0, n)
                                out.write(buf, 0, n)
                                progress(read, total)
                            }
                        }
                    }
                }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw UpdateException("安装包校验失败（SHA-256 与 SHA256SUMS.txt 不一致），已删除")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw UpdateException("无法保存安装包")
            return actual
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException("下载中断（${e.javaClass.simpleName}），稍后再试")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun get(url: String, limit: Long, hosts: Set<String>, accept: String? = null): ByteArray {
        try {
            open(url, hosts, accept).use { resp ->
                if (resp.code == 404) throw UpdateException("$repo 还没有发布任何版本")
                if (!resp.isSuccessful) throw UpdateException("返回错误 ${resp.code}")
                val source = resp.body?.source() ?: throw UpdateException("没有返回内容")
                if (source.request(limit + 1)) throw UpdateException("返回的内容太大")   // more than limit bytes
                return source.buffer.readByteArray()
            }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException("网络不通或被拦截（${e.javaClass.simpleName}）")
        } catch (e: IllegalArgumentException) {
            throw UpdateException("地址不正确")
        }
    }
}

/** `<sha256> *<文件名>` 或 `<sha256>  <文件名>` 每行一个 */
fun parseSums(text: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    for (line in text.lineSequence()) {
        val m = Regex("^([0-9a-fA-F]{64})\\s+\\*?(\\S.*)$").find(line.trim()) ?: continue
        out[m.groupValues[2].trim().substringAfterLast('/')] = m.groupValues[1].lowercase()
    }
    return out
}

/** 语义化版本比较（只看主、次、修订号；“1.3.0-rc1” 视为 1.3.0） */
object Versions {
    fun key(version: String?): Triple<Int, Int, Int>? {
        val head = (version ?: "").trim().trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val parts = head.split('.')
        if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 6 }) {
            return null
        }
        val n = parts.map { it.toInt() } + List(3 - parts.size) { 0 }
        return Triple(n[0], n[1], n[2])
    }

    /** 两个都是版本号且 latest 更大才算更新（当前版本不规范时从不认为“有更新”） */
    fun isNewer(latest: String?, current: String?): Boolean {
        val a = key(latest) ?: return false
        val b = key(current) ?: return false
        return compareValuesBy(a, b, { it.first }, { it.second }, { it.third }) > 0
    }
}
