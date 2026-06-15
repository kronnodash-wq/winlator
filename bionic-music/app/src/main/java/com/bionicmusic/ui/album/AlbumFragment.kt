package com.bionicmusic.ui.album

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bionicmusic.R
import com.bionicmusic.data.model.Song
import com.bionicmusic.databinding.FragmentAlbumBinding
import com.bionicmusic.player.BionicPlayer
import com.bionicmusic.ui.library.SongAdapter
import com.bumptech.glide.Glide

/**
 * Album / artist detail screen. Pass the song list plus display title via
 * [newInstance].
 */
class AlbumFragment : Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!

    private var songs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        songs = arguments?.getParcelableArrayList<Song>(ARG_SONGS).orEmpty()
        val artist = arguments?.getString(ARG_ARTIST).orEmpty()
        val album = arguments?.getString(ARG_ALBUM).orEmpty()

        binding.albumToolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.albumTitle.text = "$artist / $album"

        val year = songs.firstOrNull { it.year > 0 }?.year
        val countText = getString(R.string.songs_count, songs.size)
        binding.albumInfo.text = if (year != null) "$countText · $year" else countText

        songs.firstOrNull()?.let {
            Glide.with(this)
                .load(it.albumArtUri)
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.ic_album_placeholder)
                .into(binding.albumArt)
        }

        val adapter = SongAdapter(
            onClick = { pos -> BionicPlayer.setQueue(requireContext(), songs, pos) },
            onMenu = { _, _ -> }
        )
        binding.recyclerAlbumSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAlbumSongs.adapter = adapter
        adapter.submitList(songs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SONGS = "songs"
        private const val ARG_ARTIST = "artist"
        private const val ARG_ALBUM = "album"

        fun newInstance(artist: String, album: String, songs: List<Song>) =
            AlbumFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_SONGS, ArrayList(songs))
                    putString(ARG_ARTIST, artist)
                    putString(ARG_ALBUM, album)
                }
            }
    }
}
