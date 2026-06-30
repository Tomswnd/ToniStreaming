package com.toni.streaming.domain

import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.repository.AnimeRepository

/**
 * Use case that executes the full streaming extraction pipeline:
 * 1. Fetches the episode page from AnimeUnity
 * 2. Extracts the Vixcloud iframe URL
 * 3. Resolves the .m3u8 HLS manifest URL
 *
 * This must be called fresh every time the user presses "Play"
 * because the streaming tokens are ephemeral (expire quickly).
 */
class ExtractStreamUseCase(
    private val repository: AnimeRepository
) {
    /**
     * Extracts the streaming info for the given episode.
     * @param episodeUrl Full URL of the episode page on AnimeUnity
     * @return Result containing StreamInfo with .m3u8 URL and required headers
     */
    suspend operator fun invoke(episodeUrl: String): Result<StreamInfo> {
        return repository.getStreamInfo(episodeUrl)
    }
}
