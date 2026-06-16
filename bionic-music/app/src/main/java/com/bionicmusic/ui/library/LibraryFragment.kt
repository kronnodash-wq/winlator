package com.bionicmusic.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bionicmusic.MainActivity
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
        setupToolbar()
        setupRecycler()
        setupSortBar()
        loadSongs()
        observePlaying()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = getString(R.string.search_hint)
            setIconifiedByDefault(true)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    filter(newText.orEmpty())
                    return true
                }
            })
            // When SearchView collapses, reset the list
            setOnCloseListener {
                filter("")
                false
            }
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    searchItem?.expandActionView()
                    searchView?.requestFocus()
                    val imm = requireContext().getSystemService<InputMethodManager>()
                    searchView?.post {
                        imm?.showSoftInput(
                            searchView.findFocus() ?: searchView,
                            InputMethodManager.SHOW_IMPLICIT
                        )
                    }
                    true
                }
                R.id.action_online -> { (activity as? MainActivity)?.openOnline(); true }
                else -> false
            }
        }
    }

    private fun setupRecycler() {
        adapter = SongAdapter(
            onClick = { pos -> playFrom(pos) },
            onMenu = { anchor, song -> showSongMenu(anchor, song) }
        )
        binding.recyclerSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSongs.adapter = adapter
    }

    private fun setupSortBar() {
        binding.btnPlayAll.setOnClickListener { if (shownSongs.isNotEmpty()) playFrom(0) }
        binding.btnShuffle.setOnClickListener {
            if (shownSongs.isNotEmpty()) {
                playFrom((shownSongs.indices).random())
                BionicPlayer.player()?.shuffleModeEnabled = true
            }
        }
    }

    private fun loadSongs() {
        lifecycleScope.launch {
            allSongs = MusicScanner.scan(requireContext())
            sortByName()
        }
    }

    private fun sortByName() {
        shownSongs = allSongs.sortedBy { it.title.lowercase() }
        submit()
    }

    private fun filter(query: String) {
        shownSongs = if (query.isBlank()) allSongs.sortedBy { it.title.lowercase() }
        else allSongs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true)
        }
        submit()
    }

    private fun submit() {
        adapter.submitList(shownSongs)
        val empty = shownSongs.isEmpty()
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun playFrom(position: Int) {
        if (position !in shownSongs.indices) return
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
