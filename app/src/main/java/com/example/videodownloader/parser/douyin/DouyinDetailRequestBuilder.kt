package com.example.videodownloader.parser.douyin

import java.net.URLEncoder

class DouyinDetailRequestBuilder {
    fun buildParams(awemeId: String): LinkedHashMap<String, String> {
        return linkedMapOf(
            "device_platform" to "webapp",
            "aid" to "6383",
            "channel" to "channel_pc_web",
            "pc_client_type" to "1",
            "version_code" to "290100",
            "version_name" to "29.1.0",
            "cookie_enabled" to "true",
            "screen_width" to "1920",
            "screen_height" to "1080",
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Chrome",
            "browser_version" to "130.0.0.0",
            "browser_online" to "true",
            "engine_name" to "Blink",
            "engine_version" to "130.0.0.0",
            "os_name" to "Windows",
            "os_version" to "10",
            "cpu_core_num" to "12",
            "device_memory" to "8",
            "platform" to "PC",
            "downlink" to "10",
            "effective_type" to "4g",
            "from_user_page" to "1",
            "locate_query" to "false",
            "need_time_list" to "1",
            "pc_libra_divert" to "Windows",
            "publish_video_strategy_type" to "2",
            "round_trip_time" to "0",
            "show_live_replay_strategy" to "1",
            "time_list_query" to "0",
            "whale_cut_token" to "",
            "update_version_code" to "170400",
            "aweme_id" to awemeId,
            "msToken" to "",
        )
    }

    fun buildQuery(awemeId: String): String {
        return buildQuery(buildParams(awemeId))
    }

    fun buildQuery(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
    }
}
