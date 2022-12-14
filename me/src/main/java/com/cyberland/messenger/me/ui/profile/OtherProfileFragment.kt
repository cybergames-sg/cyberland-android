package com.bcm.messenger.me.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_fragment_other_profile.*

/**
 * Created by wjh on 2019-12-11
 *
 * This fragment CAN use major account context.
 */
class OtherProfileFragment : BaseFragment(), RecipientModifiedListener {
    private val TAG = "OtherProfileFragment"

    private lateinit var recipient: Recipient

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_other_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val address: Address? = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (address == null) {
            activity?.finish()
            return
        }
        recipient = Recipient.from(address, true)
        recipient.addListener(this)
        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
    }

    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            initProfile(recipient)
        }
    }

    private fun initView() {
        profile_title_bar?.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.finish()
            }
        })

        profile_photo_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleAvatarEdit(false)
        }

        profile_name_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleNameEdit(false)
        }

        profile_display_photo_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleAvatarEdit(true)
        }

        profile_display_alias_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleNameEdit(true)
        }

        profile_display_control_layout?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleReset()
        }

        profile_id_copy.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            AppUtil.saveCodeToBoard(context, recipient.address.serialize())
            AmePopup.result.succeed(it.context as FragmentActivity, getString(R.string.common_copied))
        }

        profile_id_content.text = recipient.address.serialize()

        profile_title_bar?.setCenterText(getString(R.string.me_local_profile_title))
        profile_name_item?.showRightIcon(CommonSettingItem.RIGHT_NONE)
        profile_icon_arrow?.visibility = View.GONE

        if (recipient.relationship == RecipientRepo.Relationship.STRANGER || recipient.relationship == RecipientRepo.Relationship.REQUEST) {

            profile_display_control_layout?.visibility = View.GONE
            profile_display_alias_item?.visibility = View.GONE
            profile_display_photo_item?.visibility = View.GONE
            profile_display_notice?.visibility = View.GONE
        }

        initProfile(recipient)
    }

    /**
     * ??????????????????
     */
    private fun handleNameEdit(forLocal: Boolean) {
        //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        if (forLocal) {
            startBcmActivity(accountContext, Intent(activity, EditNameActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.ME.PROFILE_FOR_LOCAL, forLocal)
                putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
            })
        }
    }

    /**
     * ??????????????????
     */
    private fun handleAvatarEdit(forLocal: Boolean) {
        val intent = Intent(activity, ImageViewActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT, true)
        intent.putExtra(ARouterConstants.PARAM.ME.PROFILE_FOR_LOCAL, forLocal)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
        startBcmActivity(accountContext, intent)
    }

    /**
     * ????????????????????????????????????profile
     */
    private fun handleReset() {
        if (recipient.localName.isNullOrEmpty() && recipient.localAvatar.isNullOrEmpty()) {
            ALog.d("ProfileActivity", "handleReset do nothing, because localName and localAvatar is null")
            return
        }
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.me_local_profile_all_reset_notice, recipient.name))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_local_profile_reset_button), AmeBottomPopup.PopupItem.CLR_RED) {
                    AmeModuleCenter.user(accountContext)?.updateNameProfile(recipient, "") {
                        AmeModuleCenter.user(accountContext)?.updateAvatarProfile(recipient, null) {
                        }
                    }
                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(activity)
    }

    /**
     * ?????????profile
     */
    private fun initProfile(recipient: Recipient) {
        profile_icon?.setPhoto(recipient, IndividualAvatarView.PROFILE_PHOTO_TYPE)
        profile_name_item?.setTip(recipient.bcmName ?: recipient.address.format())

        if (recipient.relationship != RecipientRepo.Relationship.STRANGER && recipient.relationship != RecipientRepo.Relationship.REQUEST) {
            if (recipient.localAvatar.isNullOrEmpty()) {
                profile_display_icon_notice?.visibility = View.VISIBLE
                profile_display_icon?.visibility = View.GONE
            } else {
                profile_display_icon_notice?.visibility = View.GONE
                profile_display_icon?.visibility = View.VISIBLE
                profile_display_icon?.setPhoto(recipient, IndividualAvatarView.LOCAL_PHOTO_TYPE)
            }
            if (recipient.localName.isNullOrEmpty()) {
                profile_display_alias_item?.setTip(getString(R.string.me_other_local_empty_action))
            } else {
                profile_display_alias_item?.setTip(recipient.localName ?: "")
            }
            if (recipient.localName.isNullOrEmpty() && recipient.localAvatar.isNullOrEmpty()) {
                profile_display_clear?.setTextColor(getAttrColor(R.attr.common_text_main_color))
            } else {
                profile_display_clear?.setTextColor(getAttrColor(R.attr.common_text_warn_color))
            }
        }
    }
}