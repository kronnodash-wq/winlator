package com.bionicmusic.ui.online

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
import com.bionicmusic.data.model.OnlineSong
import com.bumptech.glide.Glide

class OnlineSongAdapter(
    private val onClick: (OnlineSong) -> Unit,
    private val onMenu: (View, OnlineSong) -> Unit
) : ListAdapter<OnlineSong, OnlineSongAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val art: ImageView = view.findViewById(R.id.online_art)
        val title: TextView = view.findViewById(R.id.online_title)
        val artist: TextView = view.findViewById(R.id.online_artist)
        val duration: TextView = view.findViewById(R.id.online_duration)
        val menu: ImageButton = view.findViewById(R.id.online_menu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_online, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = getItem(position)
        holder.title.text = song.title
        holder.artist.text = song.artist
        holder.duration.text = song.durationLabel

        Glide.with(holder.art)
            .load(song.thumbnailUrl)
            .placeholder(R.drawable.ic_album_placeholder)
            .error(R.drawable.ic_album_placeholder)
            .into(holder.art)

        holder.itemView.setOnClickListener { onClick(song) }
        holder.menu.setOnClickListener { onMenu(holder.menu, song) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OnlineSong>() {
            override fun areItemsTheSame(a: OnlineSong, b: OnlineSong) = a.mediaId == b.mediaId
            override fun areContentsTheSame(a: OnlineSong, b: OnlineSong) = a == b
        }
    }
}
