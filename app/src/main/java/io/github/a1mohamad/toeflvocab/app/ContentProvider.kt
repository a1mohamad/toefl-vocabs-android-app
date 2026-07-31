package io.github.a1mohamad.toeflvocab.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import io.github.a1mohamad.toeflvocab.BuildConfig
import io.github.a1mohamad.toeflvocab.core.content.ContentError
import io.github.a1mohamad.toeflvocab.core.content.VocabCatalogLoader
import io.github.a1mohamad.toeflvocab.core.models.VocabCatalog

/**
 * Loads the bundled catalog once at launch and holds it for the lifetime of the
 * app.
 *
 * A load failure is surfaced rather than fatal: the app still opens, shows a
 * readable explanation, and Settings still works. Crashing on a packaging
 * mistake would be the worst possible outcome on a build the user sideloaded
 * from a CI artifact, where there is no console to look at.
 */
@Immutable
class ContentProvider {

    val catalog: VocabCatalog
    val loadError: String?

    constructor(context: Context) {
        var loaded: VocabCatalog
        var failure: String?
        try {
            loaded = VocabCatalogLoader.load(context)
            failure = null
        } catch (error: Exception) {
            loaded = VocabCatalog.empty
            failure = (error as? ContentError)?.localizedDescription
                ?: error.message
                ?: error.toString()
            if (BuildConfig.DEBUG) Log.e("Content", "Load failed: $error")
        }
        catalog = loaded
        loadError = failure
    }

    /** For previews and tests. */
    constructor(catalog: VocabCatalog) {
        this.catalog = catalog
        this.loadError = null
    }
}
