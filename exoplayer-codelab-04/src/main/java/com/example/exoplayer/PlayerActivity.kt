/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.exoplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.exoplayer.databinding.ActivityPlayerBinding
import org.json.JSONObject
import org.json.JSONTokener


private const val TAG = "PlayerActivity"

/**
 *
 * https://stream.mux.com/YSrK2IRqgEJN00iQzKHWGSWjRnZ00nlO4gEcej771xeeo.m3u8
 * A fullscreen activity to play audio or video streams.
 */



class PlayerActivity : AppCompatActivity() {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }

    private val playbackStateListener: Player.Listener = playbackStateListener()
    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
    }


    private fun getTimedMetadataStartTimes() : List<Float> {

        val listOfStartTimes = mutableListOf<Float>()
        val fileInString: String =
            applicationContext.assets.open("test.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONTokener(fileInString).nextValue() as JSONObject
        val jsonArray = jsonObject.getJSONArray("results")
        for (i in 0 until jsonArray.length()) {
            // iterating through json object
            val start = jsonArray.getJSONObject(i).getString("start")
            listOfStartTimes.add(start.toFloat())
        }
        return listOfStartTimes
    }



    // createMessage callback that gets passed current time
    // will parse through
    private fun updateScoreBoardView(currentStartTimeForUpdatingUI: Float) {

        // all this needs to be available at the class level, not repeated inside these funcs
        val fileInString: String =
            applicationContext.assets.open("test.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONTokener(fileInString).nextValue() as JSONObject
        val jsonArray = jsonObject.getJSONArray("results")

        for (i in 0 until jsonArray.length()) {
            // iterating through json object
            val start = jsonArray.getJSONObject(i).getString("start")

            if (start.toFloat() == currentStartTimeForUpdatingUI) {

                val homeScore = jsonArray.getJSONObject(i).getJSONObject("metadata").getJSONObject("scores").getString("home")
                val visitorScore = jsonArray.getJSONObject(i).getJSONObject("metadata").getJSONObject("scores").getString("visitor")
                val bases = jsonArray.getJSONObject(i).getJSONObject("metadata").getJSONArray("bases")


                // score
                val updatedHomeScore = findViewById<TextView>(R.id.text_view_home_score)
                updatedHomeScore.text = homeScore

                val updatedVisitorScore = findViewById<TextView>(R.id.text_view_visitor_score)
                updatedVisitorScore.text = visitorScore

                // bases
                val updatedFirstBase = findViewById<ImageView>(R.id.first_base)

                when((bases[0] === 1)) {
                    true ->  updatedFirstBase.setImageResource(R.drawable.onbase)
                    false -> updatedFirstBase.setImageResource(R.drawable.base)
                }

                val updatedSecondBase = findViewById<ImageView>(R.id.second_base)

                when((bases[1] === 1)) {
                    true -> updatedSecondBase.setImageResource(R.drawable.onbase)
                    false -> updatedSecondBase.setImageResource(R.drawable.base)
                }

                val updatedThirdBase = findViewById<ImageView>(R.id.third_base)

                when((bases[2] === 1)) {
                    true -> updatedThirdBase.setImageResource(R.drawable.onbase)
                    false -> updatedThirdBase.setImageResource(R.drawable.base)
                }

            }
        }
    }



    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
        }
    }

    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            releasePlayer()
        }
    }

    private fun initializePlayer() {
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer

                val mediaItem = MediaItem.Builder()
                    .setUri(getString(R.string.media_url_hls))
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentItem, playbackPosition)
                exoPlayer.addListener(playbackStateListener)
                exoPlayer.prepare()


                val timesForCreateMessage = getTimedMetadataStartTimes()
                timesForCreateMessage.forEach {
                    exoPlayer.createMessage { messageType: Int, payload: Any? ->
                        updateScoreBoardView(it)
                    }
                        .setPosition((it.toLong() * 1000)) // converting Int to Long, requires a Long, is this time right? Converting to milliseconds?
                        .setDeleteAfterDelivery(false)
                        .send()
                }

            }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentItem = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(playbackStateListener)
            exoPlayer.release()
        }
        player = null
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.videoView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private fun playbackStateListener() = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        val stateString: String = when (playbackState) {
            ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE      -"
            ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
            ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY     -"
            ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED     -"
            else -> "UNKNOWN_STATE             -"
        }
        Log.d(TAG, "changed state to $stateString")
    }
}