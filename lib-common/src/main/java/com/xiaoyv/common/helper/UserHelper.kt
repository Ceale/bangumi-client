package com.xiaoyv.common.helper

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.blankj.utilcode.util.SPUtils
import com.kunminx.architecture.ui.callback.UnPeekLiveData
import com.xiaoyv.blueprint.kts.launchProcess
import com.xiaoyv.blueprint.kts.toJson
import com.xiaoyv.common.api.BgmApiManager
import com.xiaoyv.common.api.exception.NeedLoginException
import com.xiaoyv.common.api.parser.entity.SettingBaseEntity
import com.xiaoyv.common.api.parser.hrefId
import com.xiaoyv.common.api.parser.impl.SignInParser.parserCheckIsLogin
import com.xiaoyv.common.api.parser.impl.parserBlockUser
import com.xiaoyv.common.api.parser.impl.parserSettingInfo
import com.xiaoyv.common.api.parser.parserFormHash
import com.xiaoyv.common.api.parser.parserSignBackground
import com.xiaoyv.common.api.response.UserEntity
import com.xiaoyv.common.kts.debugLog
import com.xiaoyv.common.kts.fromJson
import com.xiaoyv.widget.kts.sendValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class: [UserHelper]
 *
 * @author why
 * @since 11/26/23
 */
class UserHelper private constructor() {

    private val onUserInfoLiveData = MutableLiveData<UserEntity>()
    private val empty = UserEntity(isEmpty = true)

    /**
     * 全局通知删除、更新了内容等刷新通知
     */
    private val notifyAction = UnPeekLiveData<String>()

    /**
     * 单独缓存用户邮箱和密码
     */
    private fun cacheEmailAndPassword(email: String, password: String) {
        userSp.put("email", email)
        userSp.put("password", password)
    }

    /**
     * 加载缓存的用户
     */
    private fun initCache() {
        // 读取缓存用户
        val userEntity = userSp.getString(KEY_USER_INFO).orEmpty().fromJson<UserEntity>() ?: empty

        // 无登录历史跳过校验
        if (userEntity.isEmpty || userEntity.formHash.isBlank()) {
            clearUserInfo()
            debugLog { "校验缓存用户：无 登录历史 或 FormHash" }
            return
        }

        // 先载入缓存用户信息，再校验
        onUserInfoLiveData.sendValue(userEntity)

        // 有登录历史校验缓存
        checkCookie()

        // 缓存绝交用户
        cacheBreakUserIds()

        // JsonApi 授权
        UserTokenHelper.init()
    }

    /**
     * 校验缓存用户是否有效
     */
    private fun checkCookie() {
        launchProcess(Dispatchers.IO) {
            debugLog { "校验缓存用户：${currentUser.toJson(true)}" }
            refresh()

            // 检测缓存的用户信息是否有效
            val isLogin = BgmApiManager.bgmWebApi.queryLoginPage().parserCheckIsLogin()
            if (isLogin.not()) {
                debugLog { "校验缓存用户：过期清理！" }
                clearUserInfo()
                return@launchProcess
            }

            debugLog { "校验缓存用户：有效！" }
        }
    }

    /**
     * 缓存绝交用户
     */
    private fun cacheBreakUserIds() {
        launchProcess(Dispatchers.IO) {
            require(isLogin)
            val blockUserIds = BgmApiManager.bgmWebApi.queryPrivacy()
                .parserBlockUser()
                .map { it.id }

            userSp.put(KEY_BLOCK_USER, blockUserIds.toJson())
        }
    }

    /**
     * 通过查询用户设置，刷新用户信息
     */
    private suspend fun refresh(): List<SettingBaseEntity> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val document = BgmApiManager.bgmWebApi.querySettings()
                val formHash = document.parserFormHash()
                val userId = document.select(".idBadgerNeue a").hrefId()
                val settingInfo = document.parserSettingInfo()

                if (settingInfo.isNotEmpty()) {
                    saveUserInfo(userId, formHash, settingInfo)
                } else {
                    clearUserInfo()
                }
                settingInfo
            }.onFailure {
                if (it is NeedLoginException) {
                    clearUserInfo()
                }
            }.getOrDefault(emptyList())
        }
    }

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param formHash 用户 Hash
     * @param userInfo 信息
     */
    private fun saveUserInfo(userId: String, formHash: String, userInfo: List<SettingBaseEntity>) {
        val newInfo = UserEntity(id = userId, isEmpty = false, formHash = formHash)

        userInfo.forEach { item ->
            when (item.field) {
                "nickname" -> newInfo.nickname = item.value
                "picfile" -> newInfo.avatar = item.value
                "sign_input" -> newInfo.sign = item.value
                "username" -> newInfo.username = item.value
                "newbio" -> newInfo.summary = item.value
            }
        }

        // 背景
        newInfo.roomPic = newInfo.summary.parserSignBackground()

        // 更新
        onUserInfoLiveData.sendValue(newInfo)
        userSp.put(KEY_USER_INFO, newInfo.toJson())

        // 刷新绝交用户
        cacheBreakUserIds()

        // 刷新收藏数据
        CollectionHelper.syncCollection(overrideRemote = false, toast = false)
    }

    /**
     * 退出登录
     */
    private fun clearUserInfo(clearEmailAndPassword: Boolean = false) {
        BgmApiManager.resetCookie()

        // 清空
        userSp.put(KEY_USER_INFO, "")
        userSp.put(KEY_BLOCK_USER, "")
        userSp.put(KEY_USER_TOKEN, "")

        // 是否清空账户和密码
        if (clearEmailAndPassword) userSp.clear()

        // 清空收藏数据
        CollectionHelper.clearAll()

        onUserInfoLiveData.sendValue(empty)
    }


    /**
     * 获取全部的绝交用户
     */
    fun breakUsers(): List<String> {
        return userSp.getString(KEY_BLOCK_USER).fromJson<List<String>>().orEmpty()
    }

    companion object {
        private const val NAME = "user-sp"
        private const val KEY_USER_INFO = "user-info"
        private const val KEY_BLOCK_USER = "user-block"
        internal const val KEY_USER_TOKEN = "user-token"

        private val helper by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            UserHelper()
        }

        internal val userSp: SPUtils
            get() = SPUtils.getInstance(NAME)

        /**
         * 当前用户
         */
        val currentUser: UserEntity
            get() = helper.onUserInfoLiveData.value ?: helper.empty

        /**
         * 是否登录
         */
        val isLogin: Boolean
            get() = !currentUser.isEmpty

        /**
         * 获取绝交的用户
         */
        val blockUsers: List<String>
            get() = helper.breakUsers()

        /**
         * 当前用户的 FromHash
         */
        val formHash: String
            get() = currentUser.formHash

        /**
         * 缓存的邮箱
         */
        val cacheEmail: String
            get() = userSp.getString("email")

        /**
         * 缓存的密码
         */
        val cachePassword: String
            get() = userSp.getString("password")

        /**
         * 初始化缓存用户，并校验
         */
        fun initLoad() {
            helper.initCache()
        }

        /**
         * 注册用户身份信息变化
         */
        fun observeUserInfo(lifecycleOwner: LifecycleOwner, observer: Observer<UserEntity>) {
            helper.onUserInfoLiveData.observe(lifecycleOwner, observer)
        }

        /**
         * 注册用户的内容变化监听
         */
        fun observeAction(lifecycleOwner: LifecycleOwner, observer: Observer<String>) {
            helper.notifyAction.observe(lifecycleOwner, observer)
        }

        /**
         * 通知删除、更新了内容等
         */
        fun notifyActionChange(pathType: String) {
            helper.notifyAction.postValue(pathType)
        }

        fun logout() {
            launchProcess {
                withContext(Dispatchers.IO) {
                    helper.clearUserInfo(true)
                }
            }
        }

        /**
         * 刷新用户身份信息
         */
        suspend fun refresh(): List<SettingBaseEntity> {
            return helper.refresh()
        }

        /**
         * 刷新屏蔽的用户缓存
         */
        fun refreshBlockUser() {
            return helper.cacheBreakUserIds()
        }

        /**
         * 缓存用户名和密码
         */
        fun cacheEmailAndPassword(email: String, password: String) {
            return helper.cacheEmailAndPassword(email, password)
        }
    }
}