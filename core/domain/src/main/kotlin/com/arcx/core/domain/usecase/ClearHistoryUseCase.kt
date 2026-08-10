package com.arcx.core.domain.usecase

import com.arcx.core.domain.repository.HistoryRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val history: HistoryRepository,
) {
    suspend operator fun invoke() = history.clear()
}
