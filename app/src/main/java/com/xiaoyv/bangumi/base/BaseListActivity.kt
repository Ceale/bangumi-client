package com.xiaoyv.bangumi.base

import android.view.MenuItem
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.StringUtils
import com.chad.library.adapter.base.BaseDifferAdapter
import com.chad.library.adapter.base.QuickAdapterHelper
import com.chad.library.adapter.base.loadState.trailing.TrailingLoadStateAdapter
import com.xiaoyv.bangumi.databinding.ActivityListBinding
import com.xiaoyv.blueprint.base.mvvm.normal.BaseViewModelActivity
import com.xiaoyv.blueprint.kts.toJson
import com.xiaoyv.common.helper.ConfigHelper
import com.xiaoyv.common.kts.CommonString
import com.xiaoyv.common.kts.GoogleAttr
import com.xiaoyv.common.kts.debugLog
import com.xiaoyv.common.kts.initNavBack
import com.xiaoyv.common.widget.dialog.AnimeLoadingDialog
import com.xiaoyv.common.widget.scroll.AnimeLinearLayoutManager
import com.xiaoyv.widget.binder.BaseQuickDiffBindingAdapter
import com.xiaoyv.widget.dialog.UiDialog
import com.xiaoyv.widget.kts.getAttrColor
import com.xiaoyv.widget.kts.useNotNull

/**
 * Class: [BaseListActivity]
 *
 * @author why
 * @since 11/29/23
 */
abstract class BaseListActivity<T, VM : BaseListViewModel<T>> :
    BaseViewModelActivity<ActivityListBinding, VM>() {

    abstract val isOnlyOnePage: Boolean

    /**
     * 加载进度提示垂直偏移
     */
    open val loadingBias: Float = 0.5f

    internal val contentAdapter: BaseDifferAdapter<T, *> by lazy {
        onCreateContentAdapter().apply {
            ConfigHelper.configAdapterAnimation(this, binding.rvContent)
        }
    }

    internal open val needResetPositionWhenRefresh = true
    internal open val hideInputBoardWhenTouchItem = true
    internal open val debugLog = false
    internal open val hasFixedSize = false
    internal open val toolbarTitle = " "

    internal open val linearLayoutManager: LinearLayoutManager?
        get() = binding.rvContent.layoutManager as? LinearLayoutManager

    internal val adapterHelper by lazy {
        onCreateContentAdapterHelper()
    }


    open fun onCreateContentAdapterHelper(): QuickAdapterHelper {
        return QuickAdapterHelper.Builder(contentAdapter)
            .setTrailingLoadStateAdapter(object : TrailingLoadStateAdapter.OnTrailingListener {
                override fun isAllowLoading(): Boolean {
                    return binding.srlRefresh.isRefreshing.not() && isOnlyOnePage.not()
                }

                override fun onFailRetry() {
                    viewModel.loadMore()
                }

                override fun onLoad() {
                    viewModel.loadMore()
                }
            })
            .build()
    }

    abstract fun onCreateContentAdapter(): BaseQuickDiffBindingAdapter<T, *>

    @CallSuper
    override fun initView() {
        binding.toolbar.initNavBack(this)
        binding.toolbar.title = toolbarTitle

        binding.rvContent.itemAnimator = null
        binding.rvContent.setHasFixedSize(hasFixedSize)
        binding.srlRefresh.initRefresh { false }
        binding.srlRefresh.setColorSchemeColors(getAttrColor(GoogleAttr.colorPrimary))

        injectFilter(binding.flContainer)
    }


    @CallSuper
    override fun initData() {
        binding.rvContent.layoutManager = onCreateLayoutManager()
        initAdapter()
        viewModel.refresh()
    }

    /**
     * 适配器
     */
    open fun initAdapter() {
        if (isOnlyOnePage) {
            binding.rvContent.adapter = contentAdapter
        } else {
            binding.rvContent.adapter = adapterHelper.adapter
        }
    }

    open fun onCreateLayoutManager(): LinearLayoutManager {
        return AnimeLinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    @CallSuper
    override fun initListener() {
        binding.srlRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        // 隐藏软键盘
        if (hideInputBoardWhenTouchItem) {
            binding.rvContent.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    KeyboardUtils.hideSoftInput(this@BaseListActivity)
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                }

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                }
            })
        }
    }

    @CallSuper
    override fun LifecycleOwner.initViewObserver() {
        binding.stateView.initObserver(
            lifecycleOwner = this,
            loadingBias = loadingBias,
            loadingViewState = viewModel.loadingViewState,
            canShowLoading = { viewModel.isRefresh && !binding.srlRefresh.isRefreshing && canShowStateLoading() },
            canShowTip = { viewModel.isRefresh }
        )

        viewModel.onListLiveData.observe(this) {
            if (debugLog) debugLog { "List:\n " + it.toJson(true) }

            // 加载失败
            if (it == null) {
                // 刷新失败清空
                if (viewModel.isRefresh) {
                    contentAdapter.submitList(emptyList())
                }
                adapterHelper.trailingLoadState = viewModel.loadingMoreState
                onListDataError()
                return@observe
            }

            contentAdapter.submitList(it) {
                if (viewModel.isRefresh && needResetPositionWhenRefresh) {
                    linearLayoutManager?.scrollToPositionWithOffset(0, 0)
                }

                adapterHelper.trailingLoadState = viewModel.loadingMoreState

                onListDataFinish(it)

                if (viewModel.isRefresh && it.isEmpty() && viewModel.emptyCheck) {
                    binding.stateView.showTip(message = StringUtils.getString(CommonString.common_empty_tip))
                }
            }
        }

        initViewObserverExt()
    }

    open fun LifecycleOwner.initViewObserverExt() {

    }

    open fun canShowStateLoading(): Boolean {
        return true
    }

    open fun onListDataFinish(list: List<T>) {

    }

    open fun onListDataError() {

    }

    open fun injectFilter(container: FrameLayout) {

    }

    fun scrollToTop() {
        useNotNull(binding.rvContent.layoutManager as? LinearLayoutManager) {
            scrollToPositionWithOffset(0, 0)
        }
    }

    fun scrollToBottom() {
        useNotNull(binding.rvContent.layoutManager as? LinearLayoutManager) {
            val position = (contentAdapter.itemCount - 1).coerceAtLeast(0)
            scrollToPositionWithOffset(position, 0)
        }
    }

    fun smoothScrollToBottom() {
        useNotNull(binding.rvContent.layoutManager as? LinearLayoutManager) {
            val position = (contentAdapter.itemCount - 1).coerceAtLeast(0)
            smoothScrollToPosition(binding.rvContent, RecyclerView.State(), position)
        }
    }

    fun smoothScrollToTop() {
        useNotNull(binding.rvContent.layoutManager as? LinearLayoutManager) {
            smoothScrollToPosition(binding.rvContent, RecyclerView.State(), 0)
        }
    }

    override fun onCreateLoadingDialog(): UiDialog {
        return AnimeLoadingDialog(this)
    }

    @CallSuper
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        item.initNavBack(this)
        return super.onOptionsItemSelected(item)
    }
}