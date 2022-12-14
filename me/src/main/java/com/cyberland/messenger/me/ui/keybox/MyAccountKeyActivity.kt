package com.bcm.messenger.me.ui.keybox

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.content.FileProvider
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.QRExport
import com.bcm.messenger.me.R
import com.bcm.messenger.me.utils.MeConfirmDialog
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.QREncoder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_activity_my_account_key.*
import kotlinx.android.synthetic.main.me_item_keybox_account.*
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by zjl on 2018/10/30.
 */
class MyAccountKeyActivity : AccountSwipeBaseActivity() {

    private val TAG = "MyAccountKeyActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_my_account_key)

        account_my_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                handleCopyQRCode()
            }
        })

        account_my_backup.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleBackupKey()
        }

        account_my_notice.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmeModuleCenter.user(accountContext)?.gotoBackupTutorial()
        }

        fetchProfile()
    }

    override fun onResume() {
        super.onResume()
        initData()
    }

    private fun initData() {
        keybox_account_divider?.visibility = View.GONE
        keybox_account_qr?.visibility = View.VISIBLE

        val genKeyTime = AmeLoginLogic.accountHistory.getGenKeyTime(accountContext.uid)
        val backupTime = AmeLoginLogic.accountHistory.getBackupTime(accountContext.uid)

        val account = AmeLoginLogic.getAccount(accountContext.uid)
        if (account != null) {
            createAccountQRCodeWithAccountData(account)
        }

        if (genKeyTime > 0) {
            account_generate_date?.text = getString(R.string.me_str_generation_key_date, DateUtils.formatDayTime(genKeyTime))
        }

        if (backupTime > 0) {
            account_backup_date?.text = getString(R.string.me_str_backup_export_date, DateUtils.formatDayTime(backupTime))
            account_my_backup?.background = getDrawable(R.drawable.common_rectangle_8_grey_bg)
            account_my_backup?.text = getString(R.string.me_mark_as_backed_up)
            account_my_backup?.setTextColor(getAttrColor(R.attr.common_text_white_color))
            account_my_backup?.isEnabled = false
            account_my_notice?.visibility = View.INVISIBLE
        } else {
            val backupBuilder = SpannableStringBuilder()
            backupBuilder.append(getString(R.string.me_str_backup_date_export))
            val notBackup = getString(R.string.me_not_backed_up)
            backupBuilder.append(notBackup)
            val foregroundColor = ForegroundColorSpan(getAttrColor(R.attr.common_text_warn_color))
            backupBuilder.setSpan(foregroundColor, backupBuilder.length - notBackup.length - 1, backupBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            account_backup_date?.text = backupBuilder
            account_my_backup?.background = getDrawable(R.drawable.common_button_blue_bg)
            account_my_backup?.text = getString(R.string.me_finish_backup)
            account_my_backup?.setTextColor(getAttrColor(R.attr.common_text_white_color))
            account_my_backup?.isEnabled = true
            account_my_notice?.visibility = View.VISIBLE

            val noticeBuilder = SpannableStringBuilder()
            noticeBuilder.append(getString(R.string.me_account_not_backup_warning))
            noticeBuilder.append(StringAppearanceUtil.applyAppearance(getString(R.string.me_account_backup_help_description), color = getAttrColor(R.attr.common_text_blue_color)))
            account_my_notice?.text = noticeBuilder
        }
    }

    @SuppressLint("CheckResult")
    private fun fetchProfile() {
        val account = AmeLoginLogic.accountHistory.getAccount(accountContext.uid)
        val realUid: String? = account?.uid
        val name: String? = account?.name
        val avatar: String? = account?.avatar

        if (!realUid.isNullOrEmpty()) {
            val weakThis = WeakReference(this)
            Observable.create(ObservableOnSubscribe<Recipient> { emitter ->
                try {
                    val recipient = Recipient.from(accountContext, realUid, false)
                    val finalAvatar = if (BcmFileUtils.isExist(avatar)) {
                        avatar
                    } else {
                        null
                    }
                    recipient.setProfile(recipient.profileKey, name, finalAvatar)
                    emitter.onNext(recipient)
                } finally {
                    emitter.onComplete()
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ recipient ->
                        weakThis.get()?.keybox_account_openid?.text = "${getString(R.string.me_id_title)}: $realUid"
                        weakThis.get()?.keybox_account_name?.text = recipient.name
                        weakThis.get()?.keybox_account_img?.setPhoto(recipient, IndividualAvatarView.KEYBOX_PHOTO_TYPE)
                    }, {
                    })
        }
    }

    @SuppressLint("CheckResult")
    private fun createAccountQRCodeWithAccountData(accountData: AmeAccountData) {
        val weakQr = WeakReference(keybox_account_qr)

        AmePopup.loading.show(this, false)
        Observable.create(ObservableOnSubscribe<Bitmap> {
            val qrEncoder = QREncoder(QRExport.accountDataToAccountJson(accountData), dimension = 225.dp2Px(), charset = "utf-8")
            val bitmap = qrEncoder.encodeAsBitmap(!ThemeManager.isDarkTheme(this))
            it.onNext(bitmap)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    weakQr.get()?.setImageBitmap(it)
                    AmePopup.loading.dismiss()
                }, {
                    AmeAppLifecycle.failure(getString(R.string.me_switch_device_qr_fail_description), true)
                    Logger.e(it, "SwitchDeviceActivity create qr fail")
                    AmePopup.loading.dismiss()
                })
    }

    private fun handleCopyQRCode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Android 7.0??????????????????????????????
            MeConfirmDialog.showCopyBackup(this, {}, {})
        } else {
            MeConfirmDialog.showCopyBackup(this, {}, {
                copyQRCode()
            })
        }
    }

    @SuppressLint("CheckResult")
    private fun copyQRCode() {
        AmePopup.loading.show(this)
        Observable.create<Uri> {
            try {
                // ???????????????????????????
                val tempQRCodeFile = File("${cacheDir.path}/qrcode.jpg")
                if (!tempQRCodeFile.exists()) {
                    tempQRCodeFile.createNewFile()
                }
                val bitmap = (keybox_account_qr?.drawable as? BitmapDrawable)?.bitmap
                        ?: throw Exception("qr drawable is null")
                val bos = FileOutputStream(tempQRCodeFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
                bos.flush()
                bos.close()
                val uri = FileProvider.getUriForFile(this, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", tempQRCodeFile)
                it.onNext(uri)
            } catch (e: Exception) {
                it.onError(e)
            } finally {
                it.onComplete()
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    AmePopup.loading.dismiss()
                }
                .subscribe({
                    // ??????????????????
                    val targetIntents = filterPackages(it)
                    if (targetIntents.isEmpty()) {
                        // ???????????????????????????
                        AmePopup.center.newBuilder()
                                .withTitle(getString(R.string.me_backup_notice))
                                .withContent(getString(R.string.me_backup_have_no_notes_app))
                                .withOkTitle(getString(R.string.common_popup_ok))
                                .show(this)
                        return@subscribe
                    } else {
                        // ???????????????????????????
                        val chooseIntent = Intent.createChooser(targetIntents.removeAt(0), getString(R.string.me_backup_share_to))
                        chooseIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray<Array<Parcelable>>(emptyArray()))
                        startActivity(chooseIntent)
                    }
                }, {
                    ALog.e(TAG, "copyQRCode error", it)
                    AmePopup.loading.dismiss()
                })
    }

    private fun filterPackages(uri: Uri): ArrayList<Intent> {
        val targetIntents = ArrayList<Intent>()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
        }
        val packageList = packageManager.queryIntentActivities(intent, 0)
        packageList.forEach {
            if (it.activityInfo.packageName.contains("note") || it.activityInfo.packageName.contains("memo")) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    setClassName(it.activityInfo.packageName, it.activityInfo.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    type = "image/*"
                }
                targetIntents.add(shareIntent)
            }
        }

        return targetIntents
    }

    private fun handleBackupKey() {
        MeConfirmDialog.showBackupComplete(this, {}, {
            AmeLoginLogic.saveCurrentBackup(accountContext, AmeTimeUtil.localTimeSecond())
            initData()
        })
    }
}