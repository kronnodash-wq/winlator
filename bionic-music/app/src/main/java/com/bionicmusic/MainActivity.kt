package com.bionicmusic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bionicmusic.databinding.ActivityMainBinding
import com.bionicmusic.notification.MusicNotificationManager
import com.bionicmusic.player.BionicPlayer
import com.bionicmusic.service.MusicService
import com.bionicmusic.ui.library.LibraryFragment
import com.bionicmusic.ui.online.OnlineFragment
import com.bionicmusic.ui.player.PlayerFragment
import com.bionicmusic.util.ColorExtractor
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var playerVisible = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showLibrary()
    }

    /** Handles notification button taps. */
    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicNotificationManager.ACTION_PLAY_PAUSE -> BionicPlayer.playPause()
                MusicNotificationManager.ACTION_NEXT -> BionicPlayer.next()
                MusicNotificationManager.ACTION_PREV -> BionicPlayer.prev()
                MusicNotificationManager.ACTION_SHUFFLE -> BionicPlayer.toggleShuffle()
                MusicNotificationManager.ACTION_LYRICS -> openPlayer()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startMusicService()
        setupMiniPlayer()
        observePlayer()
        registerNotifReceiver()
        requestAudioPermission()

        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayer()
    }

    private fun startMusicService() {
        val intent = Intent(this, MusicService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestAudioPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            showLibrary()
        } else {
            permissionLauncher.launch(perm)
        }
        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        }
    }

    // -------- Fragment navigation --------

    private fun showLibrary() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            replaceFragment(LibraryFragment(), addToBackStack = false)
        }
    }

    fun openOnline() = replaceFragment(OnlineFragment(), addToBackStack = true)

    fun replaceFragment(fragment: Fragment, addToBackStack: Boolean) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .apply { if (addToBackStack) addToBackStack(null) }
            .commit()
    }

    fun launchEqualizer() {
        val sessionId = BionicPlayer.player()?.audioSessionId ?: AudioEffect.ERROR
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        try {
            startActivityForResult(intent, 0)
        } catch (_: Exception) {
            // No system EQ available on this device.
        }
    }

    // -------- Mini player --------

    private fun setupMiniPlayer() {
        binding.miniPlayer.miniPlayerBar.setOnClickListener { openPlayer() }
        binding.miniPlayer.miniPlay.setOnClickListener { BionicPlayer.playPause() }
        binding.miniPlayer.miniNext.setOnClickListener { BionicPlayer.next() }
        binding.miniPlayer.miniPrev.setOnClickListener { BionicPlayer.prev() }
        binding.miniPlayer.miniQueue.setOnClickListener { openPlayer(openQueue = true) }
        binding.miniPlayer.miniPlayerBar.visibility = View.GONE
    }

    private fun observePlayer() {
        lifecycleScope.launch {
            combine(BionicPlayer.currentIndex, BionicPlayer.isPlaying) { idx, playing ->
                idx to playing
            }.collect { (_, playing) ->
                val song = BionicPlayer.current
                if (song == null) {
                    binding.miniPlayer.miniPlayerBar.visibility = View.GONE
                } else {
                    binding.miniPlayer.miniPlayerBar.visibility = View.VISIBLE
                    val titleView = binding.miniPlayer.miniPlayerBar
                        .findViewById<TextView>(R.id.mini_title)
                    val artistView = binding.miniPlayer.miniPlayerBar
                        .findViewById<TextView>(R.id.mini_artist)
                    val art = binding.miniPlayer.miniPlayerBar
                        .findViewById<ImageView>(R.id.mini_art)
                    val playBtn = binding.miniPlayer.miniPlayerBar
                        .findViewById<ImageButton>(R.id.mini_play)
                    titleView.text = song.title
                    artistView.text = song.artist
                    playBtn.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    Glide.with(this@MainActivity)
                        .load(song.albumArtUri)
                        .placeholder(R.drawable.ic_album_placeholder)
                        .into(art)
                    // Dynamic background from album art palette.
                    val color = ColorExtractor.fromUri(this@MainActivity, song.albumArtUri)
                    binding.miniPlayer.miniPlayerBar.setBackgroundColor(color)
                }
            }
        }
    }

    // -------- Full player overlay --------

    fun openPlayer(openQueue: Boolean = false) {
        if (BionicPlayer.current == null) return
        binding.playerContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.player_container, PlayerFragment.newInstance(openQueue))
            .commit()
        springIn(binding.playerContainer)
        playerVisible = true
    }

    fun closePlayer() {
        springOut(binding.playerContainer) {
            binding.playerContainer.visibility = View.GONE
            supportFragmentManager.findFragmentById(R.id.player_container)?.let {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        playerVisible = false
    }

    private fun springIn(view: View) {
        view.translationY = view.height.toFloat().takeIf { it > 0 } ?: 2000f
        SpringAnimation(view, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    private fun springOut(view: View, end: () -> Unit) {
        val target = (view.height.toFloat()).takeIf { it > 0 } ?: 2000f
        SpringAnimation(view, SpringAnimation.TRANSLATION_Y, target).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            addEndListener { _, _, _, _ -> end() }
            start()
        }
    }

    override fun onBackPressed() {
        when {
            playerVisible -> closePlayer()
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            else -> super.onBackPressed()
        }
    }

    private fun registerNotifReceiver() {
        val filter = IntentFilter().apply {
            addAction(MusicNotificationManager.ACTION_PLAY_PAUSE)
            addAction(MusicNotificationManager.ACTION_NEXT)
            addAction(MusicNotificationManager.ACTION_PREV)
            addAction(MusicNotificationManager.ACTION_SHUFFLE)
            addAction(MusicNotificationManager.ACTION_LYRICS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(notifReceiver, filter)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(notifReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"

        @Suppress("unused")
        fun start(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }

        @Suppress("unused")
        private fun serviceComponent(context: Context) =
            ComponentName(context, MusicService::class.java)
    }
}
