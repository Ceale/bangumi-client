package com.xiaoyv.bangumi.ui.discover

import android.view.MenuItem
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.tabs.TabLayoutMediator
import com.xiaoyv.bangumi.databinding.FragmentDiscoverBinding
import com.xiaoyv.bangumi.helper.RouteHelper
import com.xiaoyv.bangumi.ui.MainViewModel
import com.xiaoyv.blueprint.base.mvvm.normal.BaseViewModelFragment
import com.xiaoyv.common.helper.ConfigHelper
import com.xiaoyv.common.kts.CommonDrawable
import com.xiaoyv.common.kts.CommonString
import com.xiaoyv.common.widget.dialog.AnimeLoadingDialog
import com.xiaoyv.widget.callback.setOnFastLimitClickListener
import com.xiaoyv.widget.dialog.UiDialog
import com.xiaoyv.widget.kts.adjustScrollSensitivity

/**
 * Class: [DiscoverFragment]
 *
 * @author why
 * @since 11/24/23
 */
class DiscoverFragment : BaseViewModelFragment<FragmentDiscoverBinding, DiscoverViewModel>() {

    private val activityViewModel: MainViewModel by activityViewModels()

    private val vpAdapter by lazy {
        DiscoverAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
    }

    private val tabLayoutMediator by lazy {
        TabLayoutMediator(binding.tableLayout, binding.vpContent) { tab, position ->
            tab.text = vpAdapter.tabs[position].title
        }
    }

    internal val vp
        get() = binding.vpContent

    override fun initView() {

    }

    override fun initData() {
        binding.vpContent.offscreenPageLimit = vpAdapter.itemCount
        binding.vpContent.adjustScrollSensitivity(ConfigHelper.vpTouchSlop.toFloat())
        binding.vpContent.adapter = vpAdapter
        tabLayoutMediator.attach()
    }

    override fun initListener() {
        binding.toolbar.menu.apply {
            add(getString(CommonString.common_search))
                .setIcon(CommonDrawable.ic_search)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                .setOnMenuItemClickListener {
                    RouteHelper.jumpSearch()
                    true
                }
        }

        binding.bgm.setOnFastLimitClickListener {
            RouteHelper.jumpWeb(url = "https://github.com/xiaoyvyv/bangumi", forceBrowser = true)
        }

        binding.flBgm.setOnFastLimitClickListener {
            viewModel.queryOnlineCount(showLoading = true)
        }
    }

    override fun LifecycleOwner.initViewObserver() {
        activityViewModel.onDiscoverPageIndex.observe(this) {
            binding.vpContent.setCurrentItem(it, true)
        }

        // 更新在线人数
        viewModel.onOnlineCountLiveData.observe(this) {
            if (it != 0) binding.tvOnline.text = buildString {
                append("Online:")
                append(it)
            }
        }
    }

    override fun createLoadingDialog(): UiDialog {
        return AnimeLoadingDialog(requireActivity())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabLayoutMediator.detach()
    }

    companion object {
        fun newInstance(): DiscoverFragment {
            return DiscoverFragment()
        }
    }
}