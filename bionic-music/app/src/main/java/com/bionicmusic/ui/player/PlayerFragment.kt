package com.bionicmusic.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bionicmusic.MainActivity
import com.bionicmusic.R
import com.bionicmusic.databinding.FragmentPlayerBinding
import com.bionicmusic.player.BionicPlayer
import com.bionicmusic.ui.queue.QueueFragment
import com.bumptech.glide.Glide
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var userSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wireControls()
        observe()
        startProgressLoop()

        if (arguments?.getBoolean(ARG_OPEN_QUEUE) == true) openQueue()
    }

    private fun wireControls() {
        binding.btnClose.setOnClickListener { (activity as? MainActivity)?.closePlayer() }
        binding.ctrlPlay.setOnClickListener { BionicPlayer.playPause() }
        binding.ctrlNext.setOnClickListener { BionicPlayer.next() }
        binding.ctrlPrev.setOnClickListener { BionicPlayer.prev() }
        binding.ctrlShuffle.setOnClickListener { BionicPlayer.toggleShuffle() }
        binding.btnMute.setOnClickListener { BionicPlayer.toggleMute() }
        binding.btnEq.setOnClickListener { (activity as? MainActivity)?.launchEqualizer() }
        binding.btnQueue.setOnClickListener { openQueue() }
        binding.ctrlLyrics.setOnClickListener { openQueue() }
        binding.btnMenu.setOnClickListener { showMenu(it) }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                userSeeking = false
                val dur = BionicPlayer.durationMs
                if (dur > 0) BionicPlayer.seekTo((s!!.progress.toLong() * dur) / 1000)
            }
        })
    }

    private fun openQueue() {
        childFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, 0, 0, R.anim.slide_out_right)
            .add(R.id.player_root, QueueFragment(), "queue")
            .addToBackStack("queue")
            .commit()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            BionicPlayer.isPlaying.collect { playing ->
                binding.ctrlPlay.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            BionicPlayer.currentIndex.collect { bindSong() }
        }
        bindSong()
    }

    private fun bindSong() {
        val song = BionicPlayer.current ?: return
        binding.playerTitle.text = song.title
        binding.playerArtist.text = song.artist
        Glide.with(this)
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_album_placeholder)
            .error(R.drawable.ic_album_placeholder)
            .into(binding.playerArt)
    }

    private fun startProgressLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val dur = BionicPlayer.durationMs
                val pos = BionicPlayer.positionMs
                if (!userSeeking && dur > 0) {
                    binding.seekbar.progress = ((pos * 1000) / dur).toInt()
                }
                binding.timeCurrent.text = formatTime(pos)
                binding.timeTotal.text = formatTime(dur)
                delay(500)
            }
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(R.string.ecualizador)
            setOnMenuItemClickListener {
                (activity as? MainActivity)?.launchEqualizer(); true
            }
            show()
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_OPEN_QUEUE = "open_queue"
        fun newInstance(openQueue: Boolean) = PlayerFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_OPEN_QUEUE, openQueue) }
        }
    }
}
