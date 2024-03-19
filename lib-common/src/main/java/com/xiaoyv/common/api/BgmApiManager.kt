package com.xiaoyv.common.api

import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.Utils
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.xiaoyv.common.api.api.BgmJsonApi
import com.xiaoyv.common.api.api.BgmWebApi
import com.xiaoyv.common.api.converter.WebDocumentConverter
import com.xiaoyv.common.api.converter.WebHtmlConverter
import com.xiaoyv.common.api.dns.BgmDns
import com.xiaoyv.common.api.interceptor.CommonInterceptor
import com.xiaoyv.common.api.interceptor.CookieInterceptor
import com.xiaoyv.common.api.interceptor.DouBanInterceptor
import com.xiaoyv.common.api.interceptor.JsonAuthInterceptor
import com.xiaoyv.common.config.annotation.BgmPathType
import com.xiaoyv.common.config.annotation.TopicType
import com.xiaoyv.common.helper.ConfigHelper
import com.xiaoyv.common.kts.timeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


/**
 * Class: [BgmApiManager]
 *
 * @author why
 * @since 11/18/23
 */
class BgmApiManager {
    private val cookieJar by lazy {
        PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(Utils.getApp()))
    }
    private val commonInterceptor by lazy { CommonInterceptor() }
    private val douBanInterceptor by lazy { DouBanInterceptor() }
    private val cookieInterceptor by lazy { CookieInterceptor() }

    private val bgmDns by lazy { BgmDns() }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(commonInterceptor)
            .addInterceptor(douBanInterceptor)
            .addNetworkInterceptor(cookieInterceptor)
            .dns(bgmDns)
            .apply {
                if (AppUtils.isAppDebug()) {
                    addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                }
            }
            .cookieJar(cookieJar)
            .timeout(30)
            .build()
    }

    /**
     * 此 Retrofit 有 Cookie 持久化
     */
    private val webRetrofit by lazy {
        Retrofit.Builder()
            .addConverterFactory(WebHtmlConverter.create())
            .addConverterFactory(WebDocumentConverter.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .baseUrl(URL_BASE_WEB)
            .build()
    }

    /**
     * 此 Retrofit 不会自动重定向，其余的同 [webRetrofit]
     */
    private val webNoRedirectRetrofit by lazy {
        webRetrofit.newBuilder()
            .client(
                httpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            )
            .build()
    }

    /**
     * 此 Retrofit 没有 Cookie 持久化
     */
    private val apiRetrofit by lazy {
        Retrofit.Builder()
            .addConverterFactory(WebHtmlConverter.create())
            .addConverterFactory(WebDocumentConverter.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                httpClient.newBuilder()
                    .addInterceptor(JsonAuthInterceptor())
                    .cookieJar(CookieJar.NO_COOKIES)
                    .build()
            )
            .baseUrl(URL_BASE_API)
            .build()
    }

    private val bgmWebApi by lazy {
        webRetrofit.create(BgmWebApi::class.java)
    }

    private val bgmWebNoRedirectApi by lazy {
        webNoRedirectRetrofit.create(BgmWebApi::class.java)
    }

    private val bgmJsonApi by lazy {
        apiRetrofit.create(BgmJsonApi::class.java)
    }

    /**
     * 清空 Cookie
     */
    private fun resetCookie() {
        cookieJar.clear()
    }

    companion object {
        const val APP_ID = "bgm285565606da641d78"
        const val APP_SECRET = "2f6af7bc16f05f70537ec24076164d5c"
        const val APP_CALLBACK = "http://localhost/callback"
        const val URL_BASE_API = "https://api.bgm.tv"

        val URL_BASE_WEB: String
            get() = ConfigHelper.bgmBaseUrl

        /**
         * 默认的域名
         */
        val baseUrlArray by lazy {
            listOf("https://bgm.tv", "https://bangumi.tv", "https://chii.in")
        }

        private val instance by lazy { BgmApiManager() }

        val bgmJsonApi: BgmJsonApi
            get() = instance.bgmJsonApi

        val bgmWebApi: BgmWebApi
            get() = instance.bgmWebApi

        val bgmWebNoRedirectApi: BgmWebApi
            get() = instance.bgmWebNoRedirectApi

        val httpClient
            get() = instance.httpClient

        fun readBgmCookie(): List<Cookie> {
            return instance.cookieJar.loadForRequest(
                URL_BASE_WEB.toHttpUrlOrNull() ?: return emptyList()
            )
        }

        fun resetCookie() {
            instance.resetCookie()
        }

        /**
         * 构建 Referer
         */
        fun buildReferer(@BgmPathType type: String, id: String): String {
            return when (type) {
                BgmPathType.TYPE_CHARACTER -> "$URL_BASE_WEB/character/$id"
                BgmPathType.TYPE_GROUP -> "$URL_BASE_WEB/group/$id"
                BgmPathType.TYPE_PERSON -> "$URL_BASE_WEB/person/$id"
                BgmPathType.TYPE_MESSAGE_BOX -> "$URL_BASE_WEB/pm/$id.chii"
                BgmPathType.TYPE_FRIEND -> "$URL_BASE_WEB/user/$id/friends"
                BgmPathType.TYPE_INDEX -> "$URL_BASE_WEB/index/$id"
                BgmPathType.TYPE_SUBJECT -> "$URL_BASE_WEB/subject/$id"
                BgmPathType.TYPE_EP -> "$URL_BASE_WEB/subject/$id/ep"
                BgmPathType.TYPE_USER -> "$URL_BASE_WEB/user/$id"
                BgmPathType.TYPE_BLOG -> "$URL_BASE_WEB/blog/$id"
                BgmPathType.TYPE_SCORE -> "$URL_BASE_WEB/subject/$id/collections"
                else -> URL_BASE_WEB
            }
        }

        /**
         * 话题 Url
         */
        fun buildTopicUrl(topicId: String, @TopicType topicType: String): String {
            if (topicType == TopicType.TYPE_INDEX) {
                return buildReferer(BgmPathType.TYPE_INDEX, topicId)
            }
            return "$URL_BASE_WEB/rakuen/topic/$topicType/$topicId"
        }

        /**
         * 构建 Referer
         */
        fun buildUserReferer(@BgmPathType type: String, id: String): String {
            return when (type) {
                BgmPathType.TYPE_FRIEND -> "$URL_BASE_WEB/user/$id/friends"
                BgmPathType.TYPE_INDEX -> "$URL_BASE_WEB/user/$id/index"
                else -> URL_BASE_WEB
            }
        }

        /**
         * 按月份查询番剧链接
         */
        fun buildMediaTvUrl(mediaType: String, yearMonth: String): String {
            return "$URL_BASE_WEB/${mediaType}/tv/airtime/$yearMonth"
        }

        /**
         * 年鉴 URL
         */
        fun buildAlmanacUrl(id: String): String {
            return "$URL_BASE_WEB/award/$id"
        }
    }
}