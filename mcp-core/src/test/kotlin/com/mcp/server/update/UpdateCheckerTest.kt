package com.mcp.server.update

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/**
 * 1.4.4：最新版本从 releases/latest/download/SHA256SUMS.txt 读（不用 GitHub API），可以经加速站。
 * MockWebServer 同时扮演 github.com（[base]）和一个加速站（[mirror] 前缀）。
 */
class UpdateCheckerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val apk = ByteArray(200_000) { (it % 251).toByte() }
    private var sha = sha256(apk)
    private var routes = mutableMapOf<String, MockResponse>()

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.path] ?: MockResponse().setResponseCode(404)
        }
        server.start()
        publish("1.4.0")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun mirror() = Source("custom", "${base()}/m/", "加速站 test")

    /** GitHub's layout: latest/download redirects to the tagged release (a mirror answers the same on its own host) */
    private fun publish(version: String, sums: String = defaultSums(version)) {
        routes.clear()
        for (prefix in listOf("", "/m/${base()}")) {
            routes["$prefix/o/violet-mcp/releases/latest/download/SHA256SUMS.txt"] = MockResponse().setResponseCode(302)
                .setHeader("Location", "$prefix/o/violet-mcp/releases/download/v$version/SHA256SUMS.txt")
            routes["$prefix/o/violet-mcp/releases/download/v$version/SHA256SUMS.txt"] = MockResponse().setBody(sums)
            routes["$prefix/o/violet-mcp/releases/download/v$version/violet-mcp-$version-release.apk"] =
                MockResponse().setBody(Buffer().write(apk))
        }
    }

    private fun defaultSums(version: String) = "$sha *violet-mcp-$version-release.apk\n" +
        "${"1".repeat(64)} *violet-mcp-1.3.0-release.apk\n${"0".repeat(64)} *violet-mcp-relay-1.2.0-linux-amd64\n"

    private fun checker(sources: List<Source> = listOf(Source("github", "", "GitHub")),
                        hosts: Set<String> = setOf(server.hostName)) =
        UpdateChecker(repo = "o/violet-mcp", sources = sources, githubBase = base(), allowedHosts = hosts,
            requireHttps = false)

    @Test
    fun theNewestApkInTheChecksumsIsTheLatestRelease() {
        val r = checker().latest()
        assertEquals("1.4.0", r.version)
        assertEquals("violet-mcp-1.4.0-release.apk", r.apkName)
        assertEquals("https://github.com/o/violet-mcp/releases/tag/v1.4.0", r.pageUrl)
        assertEquals("${base()}/o/violet-mcp/releases/download/v1.4.0/violet-mcp-1.4.0-release.apk", r.apkUrl)
        assertEquals(sha, r.sha256)
        assertEquals("GitHub", r.source.label)
    }

    @Test
    fun downloadsAndKeepsTheApkOnlyWhenTheChecksumMatches() {
        val c = checker()
        val target = tmp.root.resolve("u/update.apk")
        assertEquals(sha, c.download(c.latest(), target))
        assertTrue(target.readBytes().contentEquals(apk))
        assertFalse(tmp.root.resolve("u/update.apk.part").exists())

        publish("1.4.0", sums = "${"a".repeat(64)} *violet-mcp-1.4.0-release.apk\n")
        target.delete()
        expectError("校验失败") { c.download(c.latest(), target) }
        assertFalse(target.exists())
        assertFalse(tmp.root.resolve("u/update.apk.part").exists())
    }

    @Test
    fun throughAMirrorEverythingComesFromTheMirror() {
        val c = checker(sources = listOf(mirror()))
        val r = c.latest()
        assertEquals("加速站 test", r.source.label)
        assertTrue(r.apkUrl.startsWith("${base()}/m/${base()}/o/violet-mcp/releases/download/v1.4.0/"))
        assertEquals(sha, c.download(r, tmp.root.resolve("m.apk")))
        assertTrue(server.takeRequest().path!!.startsWith("/m/"))
    }

    @Test
    fun theNextSourceIsTriedAndEveryFailureIsExplained() {
        val broken = Source("custom", "${base()}/broken/", "坏的来源")
        assertEquals("加速站 test", checker(sources = listOf(broken, mirror())).latest().source.label)
        routes.clear()
        expectError("坏的来源：") { checker(sources = listOf(broken, mirror())).latest() }
        expectError("加速站 test：") { checker(sources = listOf(broken, mirror())).latest() }
    }

    @Test
    fun aReleaseWithoutAnApkOrWithBadChecksumsIsRefused() {
        publish("1.4.0", sums = "${"0".repeat(64)} *violet-mcp-relay-1.2.0-linux-amd64\n")
        expectError("没有安装包") { checker().latest() }
        publish("1.4.0", sums = "not a checksum file")
        expectError("内容不对") { checker().latest() }
        routes.clear()
        expectError("还没有发布") { checker().latest() }
    }

    @Test
    fun hostsOutsideTheListAreNeverContacted() {
        expectError("拒绝连接") { checker(hosts = setOf("github.com")).latest() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aRedirectToAnotherHostIsRefused() {
        routes["/o/violet-mcp/releases/download/v1.4.0/violet-mcp-1.4.0-release.apk"] =
            MockResponse().setResponseCode(302).setHeader("Location", "http://127.0.0.2:9/evil.apk")
        val c = checker()
        expectError("拒绝连接") { c.download(c.latest(), tmp.root.resolve("x.apk")) }
    }

    @Test
    fun theDefaultCheckerOnlyTalksHttpsToGithubAndTheChosenMirror() {
        expectError("拒绝连接") { UpdateChecker(githubBase = "http://github.com").latest() }
        assertEquals(setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
            UpdateChecker.GITHUB_HOSTS)
        assertEquals("p.jackyu.cn", Sources.BUILT_IN.single().host)
        assertNull(Sources.GITHUB_SOURCE.host)
    }

    @Test
    fun sourcesFollowTheChoiceAndOnlyHttpsCustomAddresses() {
        assertEquals(listOf("github", "jackyu"), Sources.list("auto", null).map { it.key })
        assertEquals(listOf("github"), Sources.list("github", null).map { it.key })
        assertEquals(listOf("jackyu"), Sources.list("jackyu", null).map { it.key })
        assertEquals(listOf("https://gh.example.com/"), Sources.list("custom", "https://gh.example.com").map { it.prefix })
        assertEquals(listOf("github", "jackyu"), Sources.list("custom", "http://8.217.135.233:9999/").map { it.key })
        assertEquals(listOf("github", "jackyu"), Sources.list("bogus", null).map { it.key })
        assertEquals("https://p.jackyu.cn/https://github.com/a", Sources.BUILT_IN.single().url("https://github.com/a"))
        for (bad in listOf("https://github.com/", "https://u:p@gh.example.com/", "https://gh.example.com/?x=1", "", "ftp://x/")) {
            assertNull(bad, Sources.customChecked(bad))
        }
    }

    @Test
    fun checksumFilesInBothFormats() {
        val s = parseSums("${"A".repeat(64)} *dist/a.apk\n${"b".repeat(64)}  b.bin\nnot a line\n")
        assertEquals("a".repeat(64), s["a.apk"])
        assertEquals("b".repeat(64), s["b.bin"])
        assertEquals(2, s.size)
    }

    @Test
    fun versionComparison() {
        assertTrue(Versions.isNewer("1.4.0", "1.3.0"))
        assertTrue(Versions.isNewer("v1.10.0", "1.9.9"))
        assertFalse(Versions.isNewer("1.3.0", "1.3.0"))
        assertFalse(Versions.isNewer("1.3.0-rc1", "1.3.0"))
        assertTrue(Versions.isNewer("1.4.0", "1.3.0-debug-x"))      // "1.3.0-debug-x" is still 1.3.0
        assertFalse(Versions.isNewer("2.0", "unknown"))
        assertNull(Versions.key("1.2.3.4"))
        assertNull(Versions.key("abc"))
    }

    private fun expectError(part: String, block: () -> Unit) {
        try {
            block()
            fail("expected an UpdateException containing \"$part\"")
        } catch (e: UpdateChecker.UpdateException) {
            assertTrue("\"${e.message}\" should contain \"$part\"", e.message!!.contains(part))
        }
    }
}
