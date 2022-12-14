package com.bcm.messenger.wallet

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.common.utils.view.setLeftDrawable
import com.bcm.messenger.common.utils.view.setRightDrawable
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.activity.CurrencyActivity
import com.bcm.messenger.wallet.activity.WalletListActivity
import com.bcm.messenger.wallet.model.BCMWallet
import com.bcm.messenger.wallet.model.BCMWalletAccountDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.model.WalletProgressEvent
import com.bcm.messenger.wallet.presenter.ImportantLiveData
import com.bcm.messenger.wallet.presenter.WalletViewModel
import com.bcm.messenger.wallet.utils.BCMWalletManagerContainer
import com.bcm.messenger.wallet.utils.EthExchangeCalculator
import com.bcm.messenger.wallet.utils.WalletSettings
import com.bcm.messenger.wallet.utils.WalletTypesAdapter
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.wallet_fragment.*
import java.math.BigDecimal
import java.util.*

/**
 * Created by zjl on 2018/2/28.
 */
@Route(routePath = ARouterConstants.Fragment.WALLET_HOST)
class WalletFragment : BaseFragment() {

    private val TAG = "WalletFragment"

    private lateinit var mAdapter: WalletTypesAdapter

    private var mWalletSyncing = false
        set(value) {
            field = value
            home_layout?.isEnabled = !value
            home_wallet_shade?.isEnabled = !value
            mAdapter.setSyncing(value)
        }

    private var mWalletModel: WalletViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.wallet_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ALog.i(TAG, "onViewCreated")
        val statusBarHeight = AppContextHolder.APP_CONTEXT.getStatusBarHeight()
        wallet_status_fill.layoutParams = wallet_status_fill.layoutParams.apply {
            height = statusBarHeight
        }
        home_top_bg.layoutParams = home_top_bg.layoutParams.apply {
            height += statusBarHeight
        }

        home_layout.setOnRefreshListener {
            //????????????
            refreshPage(true)
        }

        home_total_balance.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            mAdapter.changeSecretMode(!mAdapter.getSecretMode())
        }


        home_balance_currency.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            val intent = Intent(context, CurrencyActivity::class.java)
            startBcmActivity(intent)
        }

        home_wallet_shade.setOnContentClickListener {
            if (!home_layout.isRefreshing) {
                refreshPage(true)
            }
        }

        home_balance_currency.setRightDrawable(R.drawable.wallet_arrow_drop_down, R.attr.common_activity_background)

        mAdapter = WalletTypesAdapter(activity
                ?: return, object : WalletTypesAdapter.WalletActionListener {

            override fun onDisplayChanged() {
                updateWalletTotal()
            }

            override fun onDetail(coinBase: String, walletList: List<WalletDisplay>) {
                if (mWalletSyncing) {
                    //mark ????????????????????????????????????????????????
                    return
                }
                val intent = Intent(activity, WalletListActivity::class.java)
                intent.putExtra(ARouterConstants.PARAM.WALLET.COIN_TYPE, coinBase)
                intent.putParcelableArrayListExtra(ARouterConstants.PARAM.WALLET.WALLET_LIST, walletList as ArrayList<WalletDisplay>)
                startBcmActivity(intent)
            }
        })
        home_wallet_list.layoutManager = LinearLayoutManager(context)
        home_wallet_list.adapter = mAdapter
        mAdapter.initHeaderFooter(home_wallet_list)
        mAdapter.hideWalletEntrance()

        initViewModel()
    }

    private fun initViewModel() {
        val activity = activity ?: return

        updateWalletTotal()

        mWalletModel = WalletViewModel.of(activity, accountContext)
        //????????????????????????,?????????view???????????????????????????????????????????????????
        mWalletModel?.eventData?.observeForever { event ->
            ALog.i(TAG, "observe event: ${event?.id}")
            when (event?.id) {
                ImportantLiveData.EVENT_SYNC -> {
                    val pe = event.data as WalletProgressEvent
                    when (pe.stage) {
                        BCMWalletManagerContainer.WalletStage.STAGE_DONE -> {
                            ALog.i(TAG, "observe stage done")
                            mWalletSyncing = false
                            home_init_progress?.visibility = View.GONE
                            setWalletFinish()
                        }
                        BCMWalletManagerContainer.WalletStage.STAGE_ERROR -> {
                            ALog.i(TAG, "observe stage error")

                            mWalletSyncing = false
                            home_init_progress?.visibility = View.GONE
                            setWalletError()
                        }
                        else -> {
                            ALog.i(TAG, "observe stage: ${pe.stage}")
                            mWalletSyncing = true
                            home_init_progress?.visibility = View.VISIBLE
                            home_init_progress?.progress = pe.progress
                        }
                    }
                }
                ImportantLiveData.EVENT_NEW -> {
                    if (event.data != null) {
                        mAdapter.addWallet((event.data as BCMWallet).toEmptyDisplayWallet())
                    }

                }
                ImportantLiveData.EVENT_NAME_CHANGED -> {
                    if (event.data != null) {
                        mAdapter.addWallet(event.data as WalletDisplay)
                    }
                }
                ImportantLiveData.EVENT_CURRENCY_CHANGED -> {
                    mAdapter.notifyWalletChanged()
                }
                ImportantLiveData.EVENT_RATE_UPDATE -> {
                    mAdapter.notifyWalletChanged()
                }
                ImportantLiveData.EVENT_BACKUP -> {
                    val showDot = event.data as Boolean?
                    if (showDot == true) {
                        showBackupDot(true)
                    } else {
                        showBackupDot(false)
                    }
                }
                ImportantLiveData.EVENT_BALANCE -> {
                    if (event.data != null) {
                        mAdapter.addWallet(event.data as WalletDisplay)
                    } else {
                        //??????????????????????????????????????????data??????????????????????????????????????????
                        refreshPage()
                    }
                }
                ImportantLiveData.EVENT_DELETE -> {
                    refreshPage()
                }
            }
        }

        refreshPage()
    }

    private fun refreshPage(initIfNeed: Boolean = false) {
        ALog.i(TAG, "refreshPage")
        home_layout?.isRefreshing = true
        mWalletModel?.queryAccountsDisplay(initIfNeed) {
            home_layout?.isRefreshing = false
            mAdapter.setWalletList(it)
            checkCoinTypeComplete(it)
        }
    }


    private fun setWalletFinish() {
        home_wallet_shade?.visibility = View.GONE
    }

    private fun setWalletError() {
        home_wallet_shade?.visibility = View.VISIBLE
        var titleSpan = StringAppearanceUtil.applyAppearance(getString(R.string.wallet_accounts_load_error_title), 20.dp2Px(), getColor(R.color.common_color_black))
        titleSpan = StringAppearanceUtil.applyAppearance(AppContextHolder.APP_CONTEXT, titleSpan, true)
        val contentSpan = SpannableStringBuilder()
        contentSpan.append(titleSpan)
        contentSpan.append("\n")
        contentSpan.append(StringAppearanceUtil.applyAppearance(getString(R.string.wallet_accounts_load_error_content), 14.dp2Px(), getColor(R.color.common_content_second_color)))
        contentSpan.append("\n")
        contentSpan.append(StringAppearanceUtil.applyAppearance(getString(R.string.wallet_accounts_load_error_action), 14.dp2Px(), getColor(R.color.common_app_primary_color)))
        home_wallet_shade.showContent(contentSpan)
    }

    private fun updateWalletTotal() {
        home_balance_currency.text = mWalletModel?.getManager()?.getCurrentCurrency() ?: ""
        if (mAdapter.getSecretMode()) {
            home_total_balance.setLeftDrawable(R.drawable.wallet_assets_hide_icon, R.attr.common_activity_background)
            home_total_balance.text = getString(R.string.wallet_secret_text)
        } else {
            home_total_balance.setLeftDrawable(R.drawable.wallet_assets_show_icon, R.attr.common_activity_background)
            val total = mAdapter.getTotalMoney().setScale(2, BigDecimal.ROUND_HALF_UP)
            home_total_balance.text = getString(R.string.wallet_home_total_balance, EthExchangeCalculator.FormatterMoney.format(total))
        }
    }

    private fun showBackupDot(show: Boolean) {

    }

    private fun checkCoinTypeComplete(displayList: List<BCMWalletAccountDisplay>) {

        var btcList: List<WalletDisplay>? = null
        var ethList: List<WalletDisplay>? = null
        for (display in displayList) {
            if (display.coinType == WalletSettings.BTC) {
                btcList = display.coinList
            } else if (display.coinType == WalletSettings.ETH) {
                ethList = display.coinList
            }
        }
        if (btcList.isNullOrEmpty() && ethList.isNullOrEmpty()) {

            mAdapter.showWalletEntrance(getString(R.string.wallet_not_init_notice)) {
                mWalletModel?.getManager()?.goForInitWallet(activity as? AppCompatActivity) {
                    mAdapter.hideWalletEntrance()
                }
            }

        } else if (ethList.isNullOrEmpty()) {

            mAdapter.showWalletEntrance(getString(R.string.wallet_eth_support_notice)) {
                mWalletModel?.getManager()?.goForCreateWallet(activity as? AppCompatActivity, WalletSettings.ETH) {
                    mAdapter.hideWalletEntrance()
                }
            }

        } else if (btcList.isNullOrEmpty()) {

            mAdapter.showWalletEntrance(getString(R.string.wallet_btc_support_notice)) {
                mWalletModel?.getManager()?.goForCreateWallet(activity as? AppCompatActivity, WalletSettings.BTC) {
                    mAdapter.hideWalletEntrance()
                }
            }

        }else {
            mAdapter.hideWalletEntrance()
        }
    }

}