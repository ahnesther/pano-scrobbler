package com.arn.scrobble.friends

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.VectorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import coil.load
import com.arn.scrobble.App
import com.arn.scrobble.LFMRequester
import com.arn.scrobble.R
import com.arn.scrobble.Stuff
import com.arn.scrobble.databinding.ContentFriendsBinding
import com.arn.scrobble.databinding.GridItemFriendBinding
import com.arn.scrobble.recents.PaletteColors
import com.arn.scrobble.ui.EndlessRecyclerViewScrollListener
import com.arn.scrobble.ui.InitialsDrawable
import com.arn.scrobble.ui.ItemClickListener
import com.arn.scrobble.ui.LoadMoreGetter
import com.arn.scrobble.ui.PaletteTransition
import com.arn.scrobble.ui.UiUtils
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import de.umass.lastfm.ImageSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Created by arn on 10/07/2017.
 */

class FriendsAdapter(
    private val fragmentBinding: ContentFriendsBinding,
    private val viewModel: FriendsVM
) : RecyclerView.Adapter<FriendsAdapter.VHUser>(), LoadMoreGetter {

    lateinit var itemClickListener: ItemClickListener
    override lateinit var loadMoreListener: EndlessRecyclerViewScrollListener

    private val shapeAppearanceModel by lazy {
        ShapeAppearanceModel.builder(
            fragmentBinding.root.context,
            R.style.roundedCornersBig,
            R.style.roundedCornersBig
        )
            .build()
    }

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        FriendsItemTouchHelper(this, viewModel)
            .attachToRecyclerView(fragmentBinding.friendsGrid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VHUser {
        val inflater = LayoutInflater.from(parent.context)
        return VHUser(GridItemFriendBinding.inflate(inflater, parent, false))
    }

    fun getViewBindingForPopup(context: Context, position: Int): GridItemFriendBinding {
        val inflater = LayoutInflater.from(context)
        val binding = GridItemFriendBinding.inflate(inflater, fragmentBinding.root, false)

        val user = viewModel.sectionedList[position] as UserSerializable
        val holder = VHUser(binding, false)
        holder.setItemData(user)
        if (!App.prefs.demoMode)
            binding.friendsName.text = (user.realname.ifEmpty { user.name })
        return binding
    }

    override fun onBindViewHolder(holder: VHUser, position: Int) {
        holder.setItemData(viewModel.sectionedList[position] as UserSerializable)
    }

    // total number of cells
    override fun getItemCount() = viewModel.sectionedList.size

    suspend fun loadFriendsRecents(username: String) {
        delay(Stuff.FRIENDS_RECENTS_DELAY)
        val glm = fragmentBinding.friendsGrid.layoutManager as? GridLayoutManager ?: return
        viewModel.loadFriendsRecents(username)
    }

    override fun getItemId(position: Int): Long {
        return (viewModel.sectionedList[position] as UserSerializable).name.hashCode().toLong()
    }

    inner class VHUser(
        private val binding: GridItemFriendBinding,
        private val clickable: Boolean = true
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        var isPinned = false
        private var friendsRecentsJob: Job? = null

        init {
            if (clickable) {
                itemView.setOnClickListener(this)
                binding.friendsPic.setOnClickListener(this)
                binding.friendsPic.isFocusable = true
            }
        }

        override fun onClick(view: View) {
            if (clickable)
                itemClickListener.call(itemView, bindingAdapterPosition)
        }

        fun setItemData(userSerializable: UserSerializable) {
            isPinned = viewModel.isPinned(userSerializable.name)

            binding.friendsName.text =
                (userSerializable.realname.ifEmpty { userSerializable.name }) +
                        (if (isPinned) " 📍" else "")

            if (App.prefs.demoMode)
                binding.friendsName.text = "User ${bindingAdapterPosition + 1}"

            val track = viewModel.lastPlayedTracksMap[userSerializable.name]
            if (track != null && track.name != null && track.name != "") {
                binding.friendsTrackLl.visibility = View.VISIBLE
                binding.friendsTitle.text = track.name
                binding.friendsSubtitle.text = track.artist
                binding.friendsDate.text = Stuff.myRelativeTime(itemView.context, track.playedWhen)

                if (track.isNowPlaying) {
                    if (binding.friendsMusicIcon.drawable == null ||
                        binding.friendsMusicIcon.drawable is VectorDrawable || binding.friendsMusicIcon.drawable is VectorDrawableCompat
                    ) {
                        UiUtils.nowPlayingAnim(binding.friendsMusicIcon, true)
                    }
                } else {
                    if (binding.friendsMusicIcon.drawable == null ||
                        binding.friendsMusicIcon.drawable is AnimatedVectorDrawable || binding.friendsMusicIcon.drawable is AnimatedVectorDrawableCompat
                    )
                        binding.friendsMusicIcon.setImageResource(R.drawable.vd_music_circle)
                }

                binding.friendsTrackFrame.setOnClickListener {
                    Stuff.launchSearchIntent(track, null)
                }
            } else {
                binding.friendsTrackLl.visibility = View.INVISIBLE
                binding.friendsTrackFrame.setOnClickListener(null)

                if (binding.friendsMusicIcon.drawable == null ||
                    binding.friendsMusicIcon.drawable is AnimatedVectorDrawable || binding.friendsMusicIcon.drawable is AnimatedVectorDrawableCompat
                )
                    binding.friendsMusicIcon.setImageResource(R.drawable.vd_music_circle)

                if (userSerializable.name !in viewModel.privateUsers &&
                    bindingAdapterPosition > -1
                ) {
                    friendsRecentsJob?.cancel()
                    friendsRecentsJob = viewModel.viewModelScope.launch(LFMRequester.ExceptionNotifier()) {
                        loadFriendsRecents(userSerializable.name)
                    }
                }
            }

            val userImgUrl = userSerializable.getWebpImageURL(ImageSize.EXTRALARGE) ?: ""

            if (userImgUrl != binding.friendsPic.tag) {
                binding.friendsPic.tag = userImgUrl
                val bgGray = ContextCompat.getColor(itemView.context, R.color.background_gray)
                val wasCached = viewModel.urlToPaletteMap[userImgUrl] != null
                val color = if (wasCached) {
                    viewModel.urlToPaletteMap[userImgUrl]!!.apply {
                        setDarkModeFrom(itemView.context)
                    }.background
                } else {
                    bgGray
                }
                val bg = itemView.background
                if (bg == null)
                    itemView.background = MaterialShapeDrawable(shapeAppearanceModel).apply {
                        setTint(color)
                    }
                else if (bg is MaterialShapeDrawable) {
                    bg.setTint(color)
                }

                binding.friendsPic
                    .load(userImgUrl) {
                        placeholder(R.drawable.vd_placeholder_user)
                        error(InitialsDrawable(itemView.context, userSerializable))
                        allowHardware(false)
                        if (!wasCached)
                            transitionFactory(PaletteTransition.Factory { palette ->
                                val paletteColors = PaletteColors(itemView.context, palette)
                                val anim = ValueAnimator.ofArgb(bgGray, paletteColors.background)
                                anim.addUpdateListener {
                                    val bg = itemView.background
                                    if (bg is MaterialShapeDrawable) {
                                        bg.setTint(it.animatedValue as Int)
                                    }
                                }

                                anim.duration = 350
                                anim.interpolator = AccelerateInterpolator()
                                anim.start()
                                viewModel.urlToPaletteMap[userImgUrl] = paletteColors
                            })
                    }
            }
        }
    }

    enum class FriendType {
        FRIEND,
        PINNED
    }
}