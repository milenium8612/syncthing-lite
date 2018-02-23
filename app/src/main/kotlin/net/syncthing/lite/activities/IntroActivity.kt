package net.syncthing.lite.activities

import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.paolorotolo.appintro.AppIntro
import com.google.zxing.integration.android.IntentIntegrator
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.lite.R
import net.syncthing.lite.databinding.FragmentIntroOneBinding
import net.syncthing.lite.databinding.FragmentIntroThreeBinding
import net.syncthing.lite.databinding.FragmentIntroTwoBinding
import net.syncthing.lite.fragments.SyncthingFragment
import net.syncthing.lite.utils.FragmentIntentIntegrator
import net.syncthing.lite.utils.Util
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.intentFor
import java.io.IOException

/**
 * Shown when a user first starts the app. Shows some info and helps the user to add their first
 * device and folder.
 */
class IntroActivity : AppIntro() {

    /**
     * Initialize fragments and library parameters.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable continue button on second slide until a valid device ID is entered.
        nextButton.setOnClickListener {
            val fragment = fragments[pager.currentItem]
            if (fragment !is IntroFragmentTwo || fragment.isDeviceIdValid()) {
                pager.goToNextSlide()
            }
        }

        addSlide(IntroFragmentOne())
        addSlide(IntroFragmentTwo())
        addSlide(IntroFragmentThree())

        setSeparatorColor(ContextCompat.getColor(this, android.R.color.primary_text_dark))
        showSkipButton(true)
        isProgressButtonEnabled = true
        pager.isPagingEnabled = false
    }

    override fun onSkipPressed(currentFragment: Fragment) {
        onDonePressed(currentFragment)
    }

    override fun onDonePressed(currentFragment: Fragment) {
        defaultSharedPreferences.edit().putBoolean(MainActivity.PREF_IS_FIRST_START, false).apply()
        startActivity(intentFor<MainActivity>())
        finish()
    }

    /**
     * Display some simple welcome text.
     */
    class IntroFragmentOne : SyncthingFragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return DataBindingUtil.inflate<FragmentIntroOneBinding>(
                    inflater, R.layout.fragment_intro_one, container, false).root
        }

        override fun onLibraryLoaded() {
            super.onLibraryLoaded()
            context?.let { SyncthingActivity.checkLocalDiscoveryPort(it) }
            libraryHandler?.configuration { config ->
                config.localDeviceName = Util.getDeviceName()
                config.persistLater()
            }
        }
    }

    /**
     * Display device ID entry field and QR scanner option.
     */
    class IntroFragmentTwo : SyncthingFragment() {

        private lateinit var binding: FragmentIntroTwoBinding

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = DataBindingUtil.inflate(inflater, R.layout.fragment_intro_two, container, false)
            binding.enterDeviceId!!.scanQrCode.setOnClickListener {
                FragmentIntentIntegrator(this@IntroFragmentTwo).initiateScan()
            }
            binding.enterDeviceId!!.scanQrCode.setImageResource(R.drawable.ic_qr_code_white_24dp)
            return binding.root
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
            val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent)
            if (scanResult?.contents != null && scanResult.contents.isNotBlank()) {
                binding.enterDeviceId!!.deviceId.setText(scanResult.contents)
                binding.enterDeviceId!!.deviceIdHolder.isErrorEnabled = false
            }
        }

        /**
         * Checks if the entered device ID is valid. If yes, imports it and returns true. If not,
         * sets an error on the textview and returns false.
         */
        fun isDeviceIdValid(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId!!.deviceId.text.toString()
                Util.importDeviceId(libraryHandler, context, deviceId, { })
                true
            } catch (e: IOException) {
                binding.enterDeviceId!!.deviceId.error = getString(R.string.invalid_device_id)
                false
            }
        }
    }

    /**
     * Waits until remote device connects with new folder.
     */
    class IntroFragmentThree : SyncthingFragment() {

        private lateinit var binding: FragmentIntroThreeBinding

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = DataBindingUtil.inflate(inflater, R.layout.fragment_intro_three, container, false)
            return binding.root
        }

        override fun onLibraryLoaded() {
            super.onLibraryLoaded()
            libraryHandler?.library { config, client, _ ->
                client.addOnConnectionChangedListener(this::onConnectionChanged)
                val deviceId = config.localDeviceId.deviceId
                val desc = activity?.getString(R.string.intro_page_three_description, "<b>$deviceId</b>")
                binding.description.text = Html.fromHtml(desc)
            }
        }

        private fun onConnectionChanged(deviceId: DeviceId) {
            libraryHandler?.library { config, client, _ ->
                if (config.folders.isNotEmpty()) {
                    client.removeOnConnectionChangedListener(this::onConnectionChanged)
                    (activity as IntroActivity?)?.onDonePressed(this)
                }
            }
        }
    }
}