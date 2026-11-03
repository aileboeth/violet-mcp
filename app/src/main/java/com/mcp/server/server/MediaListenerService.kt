package com.mcp.server.server

import android.service.notification.NotificationListenerService

/**
 * 1.4.1：只为 now_playing 存在。Android 只允许拥有“通知使用权”的应用读取媒体会话（正在播放什么）。
 * 这个服务不处理任何通知：没有覆盖 onNotificationPosted，不读取、不保存、不转发通知内容。
 */
class MediaListenerService : NotificationListenerService()
