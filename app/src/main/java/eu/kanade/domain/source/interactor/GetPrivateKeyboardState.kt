package eu.kanade.domain.source.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.isIncognitoModeEnabled
import eu.kanade.tachiyomi.source.isPrivateKeyboardEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.service.SourceManager

class GetPrivateKeyboardState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
) {
    fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        if (sourceId == null) return false
        val source = sourceManager.get(sourceId) ?: return false
        return source.isIncognitoModeEnabled() || source.isPrivateKeyboardEnabled()
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()

        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoExtensions().changes(),
            sourcePreferences.privateKeyboardExtensions().changes(),
        ) { incognito, incognitoExtensions, privateKeyboardExtensions ->
            val source = sourceManager.get(sourceId) ?: return@combine incognito
            incognito ||
                source.isIncognitoModeEnabled(incognitoExtensions) ||
                source.isPrivateKeyboardEnabled(privateKeyboardExtensions)
        }.distinctUntilChanged()
    }
}
