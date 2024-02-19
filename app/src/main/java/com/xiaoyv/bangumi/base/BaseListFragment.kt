package com.xiaoyv.bangumi.base

import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.chad.library.adapter.base.BaseDifferAdapter
import com.chad.library.adapter.base.QuickAdapterHelper
import com.chad.library.adapter.base.loadState.trailing.TrailingLoadStateAdapter
import com.xiaoyv.bangumi.databinding.FragmentListBinding
import com.xiaoyv.blueprint.base.mvvm.normal.BaseViewModelFragment
import com.xiaoyv.blueprint.kts.toJson
import com.xiaoyv.common.helper.ConfigHelper
import com.xiaoyv.common.kts.GoogleAttr
import com.xiaoyv.common.kts.debugLog
import com.xiaoyv.common.widget.scroll.AnimeLinearLayoutManager
import com.xiaoyv.widget.kts.getAttrColor

/**
 * Class: [BaseListFragment]
 *
 * @author why
 * @since 11/29/23
 */
abstract class BaseListFragment<T, VM : BaseListViewModel<T>> :
    BaseViewModelFragment<FragmentListBinding, VM>() {

    abstract val isOnlyOnePage: Boolean

    /**
     * 加载进度提示垂直偏移
     */
    open val loadingBias: Float = 0.5f

    /**
     * 刷新时是否滑动到顶部
     */
    open val scrollTopWhenRefresh: Boolean = true

    open val hasFixedSize = false

    internal open val layoutManager: LinearLayoutManager?
        get() = binding.rvContent.layoutManager as? LinearLayoutManager

    internal val contentAdapter: BaseDifferAdapter<T, *> by lazy {
        onCreateContentAdapter().apply {
            ConfigHelper.configAdapterAnimation(this, binding.rvContent)
        }
    }

    private val adapterHelper by lazy {
        QuickAdapterHelper.Builder(contentAdapter)
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

    abstract fun onCreateContentAdapter(): BaseDifferAdapter<T, *>

    @CallSuper
    override fun initView() {
        binding.srlRefresh.initRefresh { false }
        binding.srlRefresh.setColorSchemeColors(hostActivity.getAttrColor(GoogleAttr.colorPrimary))

        injectFilter(binding.flContainer)
    }

    @CallSuper
    override fun initData() {
        binding.rvContent.setHasFixedSize(hasFixedSize)
        binding.rvContent.layoutManager = onCreateLayoutManager()
        refreshAdapter()
    }

    override fun initFinish() {
        autoInitData()
    }

    fun refreshAdapter() {
        viewModel.isOnlyOnePage = isOnlyOnePage
        initAdapter()
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

    open fun onCreateLayoutManager(): LayoutManager {
        return AnimeLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    }

    @CallSuper
    override fun initListener() {
        binding.srlRefresh.setOnRefreshListener {
            viewModel.refresh()
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
            debugLog { "List:\n " + it.toJson(true) }
            // 加载失败
            if (it == null) {
                // 刷新失败清空
                if (viewModel.isRefresh) {
                    contentAdapter.submitList(emptyList())
                }
                adapterHelper.trailingLoadState = viewModel.loadingMoreState
                return@observe
            }

            contentAdapter.submitList(it) {
                if (viewModel.isRefresh && scrollTopWhenRefresh) {
                    layoutManager?.scrollToPositionWithOffset(0, 0)
                }

                adapterHelper.trailingLoadState = viewModel.loadingMoreState

                onListDataFinish(it)
            }
        }

        initViewObserverExt()
    }

    open fun autoInitData() {
        viewModel.refresh()
    }

    open fun canShowStateLoading(): Boolean {
        return true
    }

    open fun LifecycleOwner.initViewObserverExt() {

    }

    open fun onListDataFinish(list: List<T>) {

    }

    open fun injectFilter(container: FrameLayout) {

    }
}