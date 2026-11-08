package com.mcp.server.update

/**
 * 应用更新的下载来源（Violet MCP 1.4.4；用户 2026-11-08：国内手机连不上 GitHub 时应用内更新也要能用）。
 *
 * GitHub 加速站 = 放在 github.com 地址前面的一段前缀（"https://p.jackyu.cn/" + "https://github.com/…"）。
 * 加速站是别人的服务器，但改不了安装包：安装前仍然核对 SHA-256、包名、版本号和签名证书（[UpdateChecker]、
 * AppUpdater），签名不是本应用的安装包系统也装不上。
 *
 * 只用 HTTPS：本应用不允许任何明文连接（usesCleartextTraffic=false），所以 Violet 服务器上可选的
 * HTTP 加速站 8.217.135.233 这里没有。
 */
data class Source(val key: String, val prefix: String, val label: String) {
    val direct: Boolean get() = prefix.isEmpty()
    fun url(githubUrl: String): String = prefix + githubUrl

    /** 前缀里的主机名（GitHub 本身为 null） */
    val host: String? get() = if (direct) null else prefix.substringAfter("://").substringBefore('/').substringBefore(':')
}

object Sources {
    const val AUTO = "auto"
    const val GITHUB = "github"
    const val JACKYU = "jackyu"
    const val CUSTOM = "custom"

    val GITHUB_SOURCE = Source(GITHUB, "", "GitHub")
    val BUILT_IN = listOf(Source(JACKYU, "https://p.jackyu.cn/", "加速站 p.jackyu.cn"))

    /** 设置页的选项（值, 显示的文字），第一个是默认值 */
    val CHOICES = listOf(
        AUTO to "自动",
        GITHUB to "只用 GitHub",
        JACKYU to "只用 p.jackyu.cn",
        CUSTOM to "自定义",
    )

    private val CUSTOM_RE = Regex("^https://[A-Za-z0-9.-]+(:\\d{1,5})?(/[A-Za-z0-9._~%!$&'()*+,;=:@-]*)*$")

    /** 用户填的加速地址：https、没有用户名 / 查询参数、不是 github.com 本身；规范成以 "/" 结尾。不合格返回 null */
    fun customChecked(text: String?): String? {
        val value = (text ?: "").trim()
        if (value.isEmpty() || value.length > 300 || !CUSTOM_RE.matches(value)) return null
        if (value.lowercase().startsWith("https://github.com")) return null
        return if (value.endsWith("/")) value else "$value/"
    }

    fun choiceChecked(value: String?): String = value?.takeIf { v -> CHOICES.any { it.first == v } } ?: AUTO

    /** 按顺序要试的来源。“自定义”但地址不能用时，按“自动”处理 */
    fun list(choice: String?, custom: String?): List<Source> = when (choiceChecked(choice)) {
        GITHUB -> listOf(GITHUB_SOURCE)
        JACKYU -> BUILT_IN.filter { it.key == JACKYU }
        CUSTOM -> customChecked(custom)?.let { prefix ->
            listOf(Source(CUSTOM, prefix, "自定义加速地址 " + prefix.substringAfter("://").substringBefore('/')))
        } ?: (listOf(GITHUB_SOURCE) + BUILT_IN)
        else -> listOf(GITHUB_SOURCE) + BUILT_IN
    }
}
