package com.bionicmusic.ui.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bionicmusic.R
import com.bionicmusic.databinding.FragmentQueueBinding
import com.bionicmusic.player.BionicPlayer
import com.bionicmusic.ui.library.SongAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QueueFragment : Fragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private var userSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseQueue.setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = SongAdapter(
            onClick = { pos -> BionicPlayer.playIndex(pos) },
            onMenu = { _, _ -> /* queue rows have no context menu */ }
        )
        binding.recyclerQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerQueue.adapter = adapter

        binding.queuePlay.setOnClickListener { BionicPlayer.playPause() }
        binding.queueNext.setOnClickListener { BionicPlayer.next() }
        binding.queuePrev.setOnClickListener { BionicPlayer.prev() }
        binding.queueShuffle.setOnClickListener { BionicPlayer.toggleShuffle() }
        binding.queueLyrics.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.queueSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                userSeeking = false
                val dur = BionicPlayer.durationMs
                if (dur > 0 && s != null) {
                    BionicPlayer.seekTo((s.progress.toLong() * dur) / 1000)
                }
            }
        })

        observe()
        startProgressLoop()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            BionicPlayer.queue.collect { list ->
                if (_binding == null) return@collect
                adapter.submitList(list)
                updateCounter()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            BionicPlayer.currentIndex.collect {
                if (_binding == null) return@collect
                adapter.highlightMediaId = BionicPlayer.current?.mediaId
                updateCounter()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            BionicPlayer.isPlaying.collect { playing ->
                _binding?.queuePlay?.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        }
    }

    private fun updateCounter() {
        val b = _binding ?: return
        val total = BionicPlayer.queue.value.size
        val pos = BionicPlayer.currentIndex.value + 1
        b.queueCounter.text = "$pos/$total"
    }

    private fun startProgressLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val b = _binding
                if (b != null) {
                    val dur = BionicPlayer.durationMs
                    val posMs = BionicPlayer.positionMs
                    if (!userSeeking && dur > 0) {
                        b.queueSeekbar.progress = ((posMs * 1000) / dur).toInt()
                    }
                }
                delay(500)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
