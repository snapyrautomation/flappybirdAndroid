/*
 * Copyright 2018 Konstantinos Drakonakis.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.snapyr.flappybird

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import com.github.kostasdrakonakis.androidnavigator.IntentNavigator
import com.snapyr.sdk.Properties
import com.snapyr.sdk.Snapyr
import com.snapyr.sdk.inapp.InAppActionType
import com.snapyr.sdk.inapp.InAppCallback
import com.snapyr.sdk.inapp.InAppContentType
import com.snapyr.sdk.inapp.InAppMessage
import kotlinx.android.synthetic.main.activity_splash.*


class SplashActivity : Activity(), InAppCallback {
    private var snapyrInitialized = false
    var snapyrData: SnapyrData = SnapyrData.instance

    var currentInAppMessage: InAppMessage? = null
    var currentMessageInteracted: Boolean = false

    private lateinit var identifyUserId: EditText
    private lateinit var identifyKey: EditText
    private lateinit var identifyEmail: EditText
    private lateinit var identifyName: EditText
    private lateinit var identifyPhone: EditText

    fun initializeSnapyr() {
        if (snapyrInitialized) {
            Toast.makeText(this, "Snapyr already initialized; restart app to initialize again", Toast.LENGTH_LONG).show()
            return
        }

        snapyrData.identifyUserId = identifyUserId.text.toString()
        snapyrData.identifyEmail = identifyEmail.text.toString()
        snapyrData.identifyKey = identifyKey.text.toString()
        snapyrData.identifyName = identifyName.text.toString()
        snapyrData.identifyPhone = identifyPhone.text.toString()

        var snapyr = SnapyrComponent.build(this.applicationContext)
        snapyr.onDoReset()
        snapyr.onDoIdentify()

        snapyr.registerInAppListener("splash", this)

        snapyrInitialized = true

        // disable inputs now that we've initialized
        identifyUserId.isEnabled = false
        identifyKey.isEnabled = false
        identifyEmail.isEnabled = false
        identifyName.isEnabled = false
        identifyPhone.isEnabled = false
        env.isEnabled = false
        initSnapyrButton.isEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        identifyUserId = findViewById<EditText>(R.id.identify_userid);
        identifyUserId.setText(snapyrData.identifyUserId)

        identifyKey = findViewById<EditText>(R.id.identify_key);
        identifyKey.setText(snapyrData.identifyKey)

        identifyEmail = findViewById<EditText>(R.id.identify_email);
        identifyEmail.setText(snapyrData.identifyEmail)

        identifyName = findViewById<EditText>(R.id.identify_name);
        identifyName.setText(snapyrData.identifyName)

        identifyPhone = findViewById<EditText>(R.id.identify_phone);
        identifyPhone.setText(snapyrData.identifyPhone);

        env.text = "Env: " + snapyrData.env

        initSnapyrButton.setOnClickListener {
            initializeSnapyr()
        }

        playButton.setOnClickListener {
            if (!snapyrInitialized) {
                initializeSnapyr()
            }
            IntentNavigator.startMainActivity(this)
        }

        env.setOnClickListener{
            val builder = AlertDialog.Builder(this)
                .setTitle("Choose Env!")
            builder.setPositiveButton("dev") { dialog, which ->
                snapyrData.env = "dev"
                identifyKey.setText("38bT1SbGJ0A12CJqk8DFRzypJnIylRmg")
                env.text = "Env: dev"

            }
            builder.setNeutralButton("prod") { dialog, which ->
                snapyrData.env = "prod"
                identifyKey.setText("HheJr6JJGowjvMvJGq9FqunE0h8EKAIG")
                env.text = "Env: prod"
            }
            builder.setNegativeButton("stg") { dialog, which ->
                snapyrData.env = "stg"
                identifyKey.setText("kuxCvTgQdcXAgNjrhrMP2U46VIhUi6Wz")
                env.text = "Env: stg"
            }
            builder.show()
        }

        playerStinksButton.setOnClickListener {
            onPlayerStinksClick(it)
        }

        reachedVipButton.setOnClickListener {
            onReachedVipClick(it)
        }

        setupWebView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWebView() {
        val client = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.getUrl().toString()
                if (currentInAppMessage != null) {
                    Snapyr.with(this@SplashActivity).trackInAppMessageClick(currentInAppMessage!!.ActionToken, Properties().putValue("url", url))
                }

                // Overriding `shouldOverrideUrlLoading` lets us intercept the URL when clicked, but breaks deep links.
                // Following code makes them work again
                // https://stackoverflow.com/a/53059413
                return if (url == null || url.startsWith("http://") || url.startsWith("https://")) false else try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    view.context.startActivity(intent)
                    true
                } catch (e: java.lang.Exception) {
                    Log.e("SnapyrFlappy", "shouldOverrideUrlLoading Exception: $e")
                    true
                }

            }
        }
        topBanner.webViewClient = client
        topBanner.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Any tap on the webview (even if it didn't trigger a link)
                currentMessageInteracted = true
                if (currentInAppMessage != null) {
                    Snapyr.with(this@SplashActivity).trackInAppMessageClick(currentInAppMessage!!.ActionToken)
                }
            }
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            var snapyr = SnapyrComponent.instance
            snapyr.deregisterInAppListener("splash")
            // We don't give the user a direct way to dismiss custom HTML message in this app, but treat as dismiss if they close this
            // screen without interacting with the webview
            if (currentInAppMessage != null && !currentMessageInteracted) {
                Snapyr.with(this).trackInAppMessageDismiss(currentInAppMessage!!.ActionToken)
            }
        } finally {

        }
    }

    fun onPlayerStinksClick(v: View) {
        try {
            Snapyr.with(this).track("userStinks")
        } catch (e: Exception) {
            Toast.makeText(this, "Error running track - did you forget to initialize Snapyr?", Toast.LENGTH_SHORT).show()
        }
    }

    fun onReachedVipClick(v: View) {
        try {
            Snapyr.with(this).track("userRules")
        } catch (e: Exception) {
            Toast.makeText(this, "Error running track - did you forget to initialize Snapyr?", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAction(message: InAppMessage?) {
        if (message == null || message.ActionType != InAppActionType.ACTION_TYPE_CUSTOM) {
            return
        }
        if (message.Content.type == InAppContentType.CONTENT_TYPE_HTML) {
            // keep track of it so we can read back properties like actionToken later
            currentInAppMessage = message
            currentMessageInteracted = false
            runOnUiThread {
                // Neither Brandon nor I know why this needs to be base64 but w/e
                val encodedHtml = Base64.encodeToString(message.Content.htmlContent.toByteArray(), Base64.NO_PADDING)
                topBanner.loadData(encodedHtml, "text/html", "base64")
                Snapyr.with(this).trackInAppMessageImpression(message.ActionToken);
            }
        }
        Log.e("Snapyr", message.asValueMap().toJsonObject().toString());
    }
}