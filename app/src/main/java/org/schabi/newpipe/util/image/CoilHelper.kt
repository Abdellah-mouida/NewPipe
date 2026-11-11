package org.schabi.newpipe.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.target
import coil3.request.transformations
import coil3.size.Size
import coil3.target.Target
import coil3.toBitmap
import coil3.transform.Transformation
import kotlin.math.min
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.ktx.scale

object CoilHelper {
    private val TAG = CoilHelper::class.java.simpleName

    @JvmOverloads
    fun loadBitmapBlocking(
        context: Context,
        url: String?,
        @DrawableRes placeholderResId: Int = 0
    ): Bitmap? = context.imageLoader
        .executeBlocking(getImageRequest(context, url, placeholderResId).build())
        .image
        ?.toBitmap()

    fun loadAvatar(
        target: ImageView,
        images: List<Image>
    ) {
        loadImageDefault(target, images, R.drawable.placeholder_person)
    }

    fun loadAvatar(
        target: ImageView,
        url: String?
    ) {
        loadImageDefault(target, url, R.drawable.placeholder_person)
    }

    fun loadThumbnail(
        target: ImageView,
        images: List<Image>
    ) {
        loadThumbnailAnimated(target, images)
    }

    fun loadThumbnail(
        target: ImageView,
        url: String?
    ) {
        loadThumbnailAnimated(target, url, null)
    }

    /**
     * Loads a stream thumbnail and animates the decoded image into place.
     *
     * The placeholder remains visible while the request is pending or fails.
     * View properties are reset for every request so RecyclerView reuse cannot
     * leave a recycled thumbnail transparent or scaled down.
     */
    fun loadThumbnailAnimated(
        target: ImageView,
        images: List<Image>
    ) {
        loadThumbnailAnimated(target, images, null)
    }

    fun loadThumbnailAnimated(
        target: ImageView,
        images: List<Image>,
        skeleton: View?
    ) {
        loadThumbnailAnimated(
            target,
            ImageStrategy.choosePreferredImage(images),
            skeleton
        )
    }

    private fun loadThumbnailAnimated(
        target: ImageView,
        url: String?,
        skeleton: View?
    ) {
        target.animate().cancel()
        target.alpha = 1f
        target.scaleX = 1f
        target.scaleY = 1f
        findThumbnailAnimation(target.drawable)?.stop()

        if (url.isNullOrEmpty() || !ImageStrategy.shouldLoadImages()) {
            hideThumbnailSkeleton(skeleton)
            target.setImageResource(R.drawable.placeholder_thumbnail_video)
            findThumbnailAnimation(target.drawable)?.start()
            return
        }

        val request = getImageRequest(
            target.context,
            url,
            R.drawable.placeholder_thumbnail_video
        )
            .crossfade(false)
            .target(target)
            .listener(
                onStart = {
                    target.animate().cancel()
                    target.alpha = 1f
                    target.scaleX = 1f
                    target.scaleY = 1f
                    startThumbnailSkeleton(skeleton)
                    // Start after Coil installs its placeholder drawable.
                    target.post {
                        findThumbnailAnimation(target.drawable)?.start()
                    }
                },
                onSuccess = { _, _ ->
                    findThumbnailAnimation(target.drawable)?.stop()
                    hideThumbnailSkeleton(skeleton)
                    target.animate().cancel()
                    target.alpha = 0f
                    target.scaleX = 0.985f
                    target.scaleY = 0.985f
                    target.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(280L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                },
                onError = { _, _ ->
                    hideThumbnailSkeleton(skeleton)
                    target.post {
                        findThumbnailAnimation(target.drawable)?.start()
                    }
                }
            )
            .build()

        target.context.imageLoader.enqueue(request)
    }

    /**
     * The thumbnail placeholder is a LayerDrawable containing an AnimatedVectorDrawable.
     * Find the nested animation because LayerDrawable itself is not Animatable.
     */
    private fun findThumbnailAnimation(drawable: Drawable?): Animatable? {
        drawable ?: return null
        if (drawable is Animatable) {
            return drawable
        }
        if (drawable is LayerDrawable) {
            for (index in 0 until drawable.numberOfLayers) {
                findThumbnailAnimation(drawable.getDrawable(index))?.let { return it }
            }
        }
        return null
    }

    private fun startThumbnailSkeleton(skeleton: View?) {
        skeleton ?: return
        skeleton.clearAnimation()
        skeleton.alpha = 1f
        skeleton.visibility = View.VISIBLE
        skeleton.startAnimation(
            AlphaAnimation(0.62f, 1f).apply {
                duration = 850L
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
                interpolator = DecelerateInterpolator()
            }
        )
    }

    private fun hideThumbnailSkeleton(skeleton: View?) {
        skeleton ?: return
        skeleton.clearAnimation()
        skeleton.alpha = 1f
        skeleton.visibility = View.GONE
    }

    fun loadScaledDownThumbnail(
        context: Context,
        images: List<Image>,
        target: Target
    ): Disposable {
        val url = ImageStrategy.choosePreferredImage(images)
        val request =
            getImageRequest(context, url, R.drawable.placeholder_thumbnail_video)
                .target(target)
                .transformations(
                    object : Transformation() {
                        override val cacheKey = "COIL_PLAYER_THUMBNAIL_TRANSFORMATION_KEY"

                        override suspend fun transform(
                            input: Bitmap,
                            size: Size
                        ): Bitmap {
                            if (MainActivity.DEBUG) {
                                Log.d(TAG, "Thumbnail - transform() called")
                            }

                            val notificationThumbnailWidth =
                                min(
                                    context.resources.getDimension(R.dimen.player_notification_thumbnail_width),
                                    input.width.toFloat()
                                ).toInt()

                            var newHeight = input.height / (input.width / notificationThumbnailWidth)
                            val result = input.scale(notificationThumbnailWidth, newHeight)

                            return if (result == input || !result.isMutable) {
                                // create a new mutable bitmap to prevent strange crashes on some
                                // devices (see #4638)
                                newHeight = input.height / (input.width / (notificationThumbnailWidth - 1))
                                input.scale(notificationThumbnailWidth, newHeight)
                            } else {
                                result
                            }
                        }
                    }
                ).build()

        return context.imageLoader.enqueue(request)
    }

    fun loadDetailsThumbnail(
        target: ImageView,
        images: List<Image>
    ) {
        val url = ImageStrategy.choosePreferredImage(images)
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_video, false)
    }

    fun loadBanner(
        target: ImageView,
        images: List<Image>
    ) {
        loadImageDefault(target, images, R.drawable.placeholder_channel_banner)
    }

    fun loadPlaylistThumbnail(
        target: ImageView,
        images: List<Image>
    ) {
        loadImageDefault(target, images, R.drawable.placeholder_thumbnail_playlist)
    }

    fun loadPlaylistThumbnail(
        target: ImageView,
        url: String?
    ) {
        loadImageDefault(target, url, R.drawable.placeholder_thumbnail_playlist)
    }

    private fun loadImageDefault(
        target: ImageView,
        images: List<Image>,
        @DrawableRes placeholderResId: Int
    ) {
        loadImageDefault(target, ImageStrategy.choosePreferredImage(images), placeholderResId)
    }

    private fun loadImageDefault(
        target: ImageView,
        url: String?,
        @DrawableRes placeholderResId: Int,
        showPlaceholder: Boolean = true
    ) {
        val request =
            getImageRequest(target.context, url, placeholderResId, showPlaceholder)
                .target(target)
                .build()
        target.context.imageLoader.enqueue(request)
    }

    private fun getImageRequest(
        context: Context,
        url: String?,
        @DrawableRes placeholderResId: Int,
        showPlaceholderWhileLoading: Boolean = true
    ): ImageRequest.Builder {
        // if the URL was chosen with `choosePreferredImage` it will be null, but check again
        // `shouldLoadImages` in case the URL was chosen with `imageListToDbUrl` (which is the case
        // for URLs stored in the database)
        val takenUrl = url?.takeIf { it.isNotEmpty() && ImageStrategy.shouldLoadImages() }

        return ImageRequest
            .Builder(context)
            .data(takenUrl)
            .crossfade(220)
            .error(placeholderResId)
            .memoryCacheKey(takenUrl)
            .diskCacheKey(takenUrl)
            .apply {
                if (takenUrl != null || showPlaceholderWhileLoading) {
                    placeholder(placeholderResId)
                }
            }
    }
}
