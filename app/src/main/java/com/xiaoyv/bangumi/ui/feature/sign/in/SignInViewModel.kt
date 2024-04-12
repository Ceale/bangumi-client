package com.xiaoyv.bangumi.ui.feature.sign.`in`

import androidx.lifecycle.MutableLiveData
import com.xiaoyv.blueprint.base.mvvm.normal.BaseViewModel
import com.xiaoyv.blueprint.kts.launchUI
import com.xiaoyv.common.api.BgmApiManager
import com.xiaoyv.common.api.parser.entity.SignInFormEntity
import com.xiaoyv.common.api.parser.entity.SignInResultEntity
import com.xiaoyv.common.api.parser.impl.SignInParser.parserLoginForms
import com.xiaoyv.common.api.parser.impl.SignInParser.parserLoginResult
import com.xiaoyv.common.helper.UserHelper
import com.xiaoyv.common.helper.UserTokenHelper
import com.xiaoyv.common.kts.debugLog
import com.xiaoyv.common.kts.randId
import com.xiaoyv.widget.kts.errorMsg
import com.xiaoyv.widget.kts.sendValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class: [SignInViewModel]
 *
 * @author why
 * @since 11/25/23
 */
class SignInViewModel : BaseViewModel() {
    internal val onVerifyCodeLiveData = MutableLiveData<ByteArray?>()
    internal val onLoginResultLiveData = MutableLiveData<SignInResultEntity?>()

    private var formEntity: SignInFormEntity = SignInFormEntity()

    override fun onViewCreated() {
        launchUI {
            refreshFormImpl()
            refreshVerifyImpl()
        }
    }

    fun refreshVerifyCode(loading: Boolean = false) {
        launchUI(
            state = if (loading) loadingDialogState(cancelable = false) else null,
            error = { it.printStackTrace() },
            block = {
                refreshFormImpl()
                refreshVerifyImpl()
            }
        )
    }

    private suspend fun refreshVerifyImpl() {
        onVerifyCodeLiveData.value = withContext(Dispatchers.IO) {
            val map = mapOf(randId() to "")
            BgmApiManager.bgmWebApi.queryLoginVerify(map).bytes()
        }
    }

    private suspend fun refreshFormImpl() {
        formEntity = withContext(Dispatchers.IO) {
            BgmApiManager.bgmWebApi.queryLoginPage().parserLoginForms()
        }

        val resultEntity = formEntity.loginInfo
        if (formEntity.hasLogin && resultEntity != null) {
            onLoginResultLiveData.sendValue(resultEntity)

            debugLog { "当前用户已经登录成功，无需再登录" }
        }
    }

    fun doLogin(email: String, password: String, verifyCode: String) {
        launchUI(
            state = loadingDialogState(cancelable = false),
            error = {
                it.printStackTrace()

                onLoginResultLiveData.value = SignInResultEntity(
                    success = false,
                    error = it.errorMsg
                )
            },
            block = {
                val loginResult = withContext(Dispatchers.IO) {
                    val forms = formEntity.forms
                    forms["email"] = email
                    forms["password"] = password
                    forms["captcha_challenge_field"] = verifyCode

                    BgmApiManager.bgmWebApi.doSignIn(param = forms).parserLoginResult()
                }

                // 成功
                if (loginResult.success) {
                    // 拉取用户信息
                    UserHelper.refresh()
                    UserHelper.cacheEmailAndPassword(email, password)

                    // JsonApi 授权
                    runCatching { UserTokenHelper.fetchAuthToken() }
                }

                onLoginResultLiveData.value = loginResult
            }
        )
    }
}