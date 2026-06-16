package com.bionicmusic.ui.online

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bionicmusic.R
import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.databinding.FragmentOnlineBinding
import com.bionicmusic.data.online.OnlineRepository
import com.bionicmusic.player.BionicPlayer
import com.bionicmusic.util.DownloadManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OnlineFragment : Fragment() {

    private var _binding: FragmentOnlineBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: OnlineSongAdapter
    private var results: List<OnlineSong> = emptyList()
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnlineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onlineToolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        adapter = OnlineSongAdapter(
            onClick = { song -> playOnline(song) },
            onMenu = { anchor, song -> showOnlineMenu(anchor, song) }
        )
        binding.recyclerOnline.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOnline.adapter = adapter

        binding.onlineSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(v.text.toString())
                true
            } else false
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        binding.onlineProgress.visibility = View.VISIBLE
        binding.onlineEmpty.visibility = View.GONE
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            results = OnlineRepository.searchAll(query)
            adapter.submitList(results)
            binding.onlineProgress.visibility = View.GONE
            binding.onlineEmpty.visibility =
                if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun playOnline(song: OnlineSong) {
        viewLifecycleOwner.lifecycleScope.launch {
            val url = OnlineRepository.sourceFor(song).resolveUrl(song, AudioFormat.MP3)
            if (url == null) {
                toast(getString(R.string.download_failed))
                return@launch
            }
            val player = BionicPlayer.ensure(requireContext())
            player.setMediaItem(song.toMediaItem(url))
            player.prepare()
            player.play()
        }
    }

    private fun showOnlineMenu(anchor: View, song: OnlineSong) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_song_online, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.online_descargar -> { promptFormat(song); true }
                    R.id.online_mp3 -> { download(song, AudioFormat.MP3); true }
                    R.id.online_flac -> { download(song, AudioFormat.FLAC); true }
                    R.id.online_eliminar -> { removeFromResults(song); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun promptFormat(song: OnlineSong) {
        val view = layoutInflater.inflate(R.layout.dialog_download_format, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        dialog.setContentView(view)
        view.findViewById<View>(R.id.format_mp3).setOnClickListener {
            download(song, AudioFormat.MP3); dialog.dismiss()
        }
        view.findViewById<View>(R.id.format_flac).setOnClickListener {
            download(song, AudioFormat.FLAC); dialog.dismiss()
        }
        dialog.show()
    }

    private fun download(song: OnlineSong, format: AudioFormat) {
        toast(getString(R.string.downloading))
        viewLifecycleOwner.lifecycleScope.launch {
            DownloadManager.download(requireContext(), song, format)
        }
    }

    private fun removeFromResults(song: OnlineSong) {
        results = results.filterNot { it.mediaId == song.mediaId }
        adapter.submitList(results)
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
