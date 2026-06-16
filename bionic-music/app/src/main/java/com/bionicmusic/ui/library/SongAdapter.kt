package com.bionicmusic.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bionicmusic.R
import com.bionicmusic.data.model.Song
import com.bumptech.glide.Glide

/**
 * Adapter for the local song list (library, album and queue screens).
 *
 * @param highlightMediaId optionally highlights the currently playing row in blue.
 */
class SongAdapter(
    private val onClick: (Int) -> Unit,
    private val onMenu: (View, Song) -> Unit
) : ListAdapter<Song, SongAdapter.VH>(DIFF) {

    var highlightMediaId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val art: ImageView = view.findViewById(R.id.song_art)
        val title: TextView = view.findViewById(R.id.song_title)
        val artist: TextView = view.findViewById(R.id.song_artist)
        val menu: ImageButton = view.findViewById(R.id.song_menu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist

        val nowPlaying = song.mediaId == highlightMediaId
        val color = if (nowPlaying)
            holder.itemView.context.getColor(R.color.now_playing_blue)
        else
            holder.itemView.context.getColor(R.color.text_primary)
        holder.title.setTextColor(color)

        Glide.with(holder.art)
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_album_placeholder)
            .error(R.drawable.ic_album_placeholder)
            .into(holder.art)

        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.menu.setOnClickListener { onMenu(holder.menu, song) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }
    }
}
