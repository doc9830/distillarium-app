package ru.distillarium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.distillarium.ui.DistillariumRoot
import ru.distillarium.ui.theme.DistillariumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialArticle = intent.getStringExtra(EXTRA_ARTICLE_ID)
        val initialHeading = intent.getStringExtra(EXTRA_ARTICLE_HEADING)
        setContent {
            DistillariumTheme {
                DistillariumRoot(
                    initialArticleId = initialArticle,
                    initialArticleHeading = initialHeading,
                )
            }
        }
    }

    private companion object {
        const val EXTRA_ARTICLE_ID = "ru.distillarium.extra.ARTICLE_ID"
        const val EXTRA_ARTICLE_HEADING = "ru.distillarium.extra.ARTICLE_HEADING"
    }
}
