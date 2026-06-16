package com.bionicmusic.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bionicmusic.R
import com.bionicmusic.data.local.MusicScanner
import com.bionicmusic.data.model.Song
import com.bionicmusic.databinding.FragmentLibraryBinding
import com.bionicmusic.player.BionicPlayer
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private var allSongs: List<Song> = emptyList()
    private var shownSongs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        loadSongs()
        observePlaying()
    }

    private fun setupRecycler() {
        adapter = SongAdapter(
            onClick = { pos -> playFrom(pos) },
            onMenu = { anchor, song -> showSongMenu(anchor, song) }
        )
        binding.recyclerSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSongs.adapter = adapter
    }

    private fun loadSongs() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            try {
                allSongs = MusicScanner.scan(requireContext())
            } catch (_: Exception) {
                allSongs = emptyList()
            }
            sortByName()
        }
    }

    private fun sortByName() {
        shownSongs = allSongs.sortedBy { it.title.lowercase() }
        submit()
    }

    private fun submit() {
        val b = _binding ?: return
        adapter.submitList(shownSongs)
        val empty = shownSongs.isEmpty()
        b.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun playFrom(position: Int) {
        if (position !in shownSongs.indices) return
        if (!isAdded) return
        BionicPlayer.setQueue(requireContext(), shownSongs, position)
    }

    private fun observePlaying() {
        viewLifecycleOwner.lifecycleScope.launch {
            BionicPlayer.currentIndex.collect {
                if (_binding != null) {
                    adapter.highlightMediaId = BionicPlayer.current?.mediaId
                }
            }
        }
    }

    private fun showSongMenu(anchor: View, song: Song) {
        if (!isAdded) return
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_song_local, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.song_eliminar -> { deleteSong(song); true }
                    R.id.song_compartir -> { shareSong(song); true }
                    R.id.song_tono -> { setAs(song, RingtoneType.RINGTONE); true }
                    R.id.song_alarma -> { setAs(song, RingtoneType.ALARM); true }
                    else -> false
                }
            }
            show()
        }
    }

    private enum class RingtoneType { RINGTONE, ALARM }

    private fun deleteSong(song: Song) {
        if (!isAdded) return
        try {
            requireContext().contentResolver.delete(song.uri, null, null)
            allSongs = allSongs.filterNot { it.id == song.id }
            sortByName()
        } catch (_: Exception) {
        }
    }

    private fun shareSong(song: Song) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, song.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, song.title))
    }

    private fun setAs(song: Song, type: RingtoneType) {
        if (!isAdded) return
        try {
            val rtType = if (type == RingtoneType.ALARM)
                android.media.RingtoneManager.TYPE_ALARM
            else
                android.media.RingtoneManager.TYPE_RINGTONE
            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                requireContext(), rtType, song.uri
            )
        } catch (_: Exception) {
            // Requires WRITE_SETTINGS on some devices; ignored gracefully.
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
