package pl.fithubapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pl.fithubapp.data.ExerciseDto

class ExerciseListAdapter(
    private val onExerciseClick: (ExerciseDto) -> Unit = {},
    private val onDeleteClick: ((ExerciseDto) -> Unit)? = null,
    private val showDeleteButton: Boolean = true,
    private val lifecycle: Lifecycle
) : ListAdapter<ExerciseListAdapter.ExerciseItem, ExerciseListAdapter.ExerciseViewHolder>(ExerciseDiffCallback()) {

    data class ExerciseItem(
        val exercise: ExerciseDto,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ExerciseViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    inner class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvExerciseName: TextView = itemView.findViewById(R.id.tvExerciseName)
        private val tvExerciseDescription: TextView = itemView.findViewById(R.id.tvExerciseDescription)
        private val tvExerciseInstructions: TextView = itemView.findViewById(R.id.tvExerciseInstructions)
        private val llExpandedDetails: LinearLayout = itemView.findViewById(R.id.llExpandedDetails)
        private val btnExpandExercise: ImageButton = itemView.findViewById(R.id.btnExpandExercise)
        private val btnDeleteExercise: ImageButton = itemView.findViewById(R.id.btnDeleteExercise)
        private val webViewVideo: WebView = itemView.findViewById(R.id.webViewYoutube)

        private var isExpanded = false
        private var currentVideoUrl: String? = null

        init {
            webViewVideo.settings.apply {
                javaScriptEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webViewVideo.setBackgroundColor(0) // przezroczyste
            webViewVideo.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        fun bind(item: ExerciseItem) {
            val exercise = item.exercise

            tvExerciseName.text = exercise.name ?: "Ćwiczenie"
            tvExerciseDescription.text = exercise.desc ?: "Brak opisu"
            tvExerciseInstructions.text = exercise.instructions?.mapIndexed { index, instruction ->
                "${index + 1}. $instruction"
            }?.joinToString("\n") ?: "Brak instrukcji"

            currentVideoUrl = exercise.videoUrl

            isExpanded = false
            llExpandedDetails.visibility = View.GONE
            btnExpandExercise.rotation = 0f
            stopVideo()

            webViewVideo.visibility = if (currentVideoUrl != null) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                toggleExpanded()
            }

            btnExpandExercise.setOnClickListener {
                toggleExpanded()
            }

            if (showDeleteButton && onDeleteClick != null) {
                btnDeleteExercise.visibility = View.VISIBLE
                btnDeleteExercise.setOnClickListener {
                    onDeleteClick.invoke(exercise)
                }
            } else {
                btnDeleteExercise.visibility = View.GONE
            }
        }

        private fun toggleExpanded() {
            isExpanded = !isExpanded
            llExpandedDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            // Zmiana ikony strzałki
            btnExpandExercise.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )

            if (isExpanded && currentVideoUrl != null) {
                playVideo(currentVideoUrl!!)
            } else {
                stopVideo()
            }
        }

        private fun playVideo(videoUrl: String) {
            val embedHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { 
                            width: 100%; 
                            height: 100%; 
                            background: transparent;
                            overflow: hidden; 
                        }
                        .video-container { 
                            position: relative; 
                            width: 100%; 
                            height: 0; 
                            padding-bottom: 56.25%;
                            background: transparent;
                        }
                        .video-container iframe { 
                            position: absolute; 
                            top: 0; 
                            left: 0; 
                            width: 100%; 
                            height: 100%; 
                            border: none;
                            display: block;
                        }
                    </style>
                </head>
                <body>
                    <div class="video-container">
                        <iframe 
                            src="$videoUrl?badge=0&autopause=1&autoplay=0&loop=0" 
                            allow="autoplay; fullscreen; picture-in-picture; clipboard-write" 
                            allowfullscreen
                            frameborder="0"
                            title="Vimeo Video Player">
                        </iframe>
                    </div>
                </body>
                </html>
            """.trimIndent()

            webViewVideo.loadDataWithBaseURL(
                "https://player.vimeo.com",
                embedHtml,
                "text/html",
                "UTF-8",
                null
            )
        }

        private fun stopVideo() {
            webViewVideo.loadUrl("about:blank")
            webViewVideo.clearHistory()
            webViewVideo.clearCache(true)
        }

        fun cleanup() {
            stopVideo()
        }
    }

    class ExerciseDiffCallback : DiffUtil.ItemCallback<ExerciseItem>() {
        override fun areItemsTheSame(oldItem: ExerciseItem, newItem: ExerciseItem): Boolean {
            return oldItem.exercise.id == newItem.exercise.id
        }

        override fun areContentsTheSame(oldItem: ExerciseItem, newItem: ExerciseItem): Boolean {
            return oldItem == newItem
        }
    }
}