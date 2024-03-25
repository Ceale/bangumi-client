package com.xiaoyv.bangumi.ui.feature.person

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.xiaoyv.bangumi.ui.feature.empty.EmptyFragment
import com.xiaoyv.bangumi.ui.feature.person.character.PersonCharacterFragment
import com.xiaoyv.bangumi.ui.feature.person.collect.PersonCollectFragment
import com.xiaoyv.bangumi.ui.feature.person.cooperate.PersonCooperateFragment
import com.xiaoyv.bangumi.ui.feature.person.opus.PersonOpusFragment
import com.xiaoyv.bangumi.ui.feature.person.overview.PersonOverviewFragment
import com.xiaoyv.bangumi.ui.feature.person.picture.PersonPictureFragment
import com.xiaoyv.common.config.annotation.PersonTabType
import com.xiaoyv.common.config.bean.tab.PersonTab

/**
 * Class: [PersonAdapter]
 *
 * @author why
 * @since 11/24/23
 */
class PersonAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    internal var personId = ""
    internal var isVirtual = false

    private val personTabs = listOf(
        PersonTab("概览", PersonTabType.TYPE_OVERVIEW),
        PersonTab("角色", PersonTabType.TYPE_CHARACTER),
        PersonTab("作品", PersonTabType.TYPE_OPUS),
        PersonTab("合作", PersonTabType.TYPE_COOPERATE),
        PersonTab("收藏", PersonTabType.TYPE_SAVE)
    )

    private val virtualTabs = listOf(
        PersonTab("概览", PersonTabType.TYPE_OVERVIEW),
        PersonTab("收藏", PersonTabType.TYPE_SAVE),
        PersonTab("图片", PersonTabType.TYPE_PICTURE)
    )

    internal val tabs get() = if (isVirtual) virtualTabs else personTabs

    override fun createFragment(position: Int): Fragment {
        val profileTab = tabs[position]
        val type = profileTab.type
        return when (type) {
            PersonTabType.TYPE_OVERVIEW -> PersonOverviewFragment.newInstance(personId, isVirtual)
            PersonTabType.TYPE_CHARACTER -> PersonCharacterFragment.newInstance(personId, isVirtual)
            PersonTabType.TYPE_OPUS -> PersonOpusFragment.newInstance(personId, isVirtual)
            PersonTabType.TYPE_COOPERATE -> PersonCooperateFragment.newInstance(personId, isVirtual)
            PersonTabType.TYPE_SAVE -> PersonCollectFragment.newInstance(personId, isVirtual)
            PersonTabType.TYPE_PICTURE -> PersonPictureFragment.newInstance(personId)
            else -> EmptyFragment.newInstance()
        }
    }

    override fun getItemCount(): Int {
        return tabs.size
    }
}