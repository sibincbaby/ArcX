package com.arcx.core.model

/**
 * The runtime content a workflow is fired against, assembled by whichever entry point
 * launched it (share sheet, text selection, bubble, shortcut).
 */
data class WorkflowInput(
    val text: String? = null,
    /** `Intent.EXTRA_SUBJECT` from a share — an email subject, a page title. Feeds `{{share_subject}}`. */
    val shareSubject: String? = null,
    val attachments: List<Attachment> = emptyList(),
    /** Package name of the app the user was in, when known. Feeds `{{current_app}}`. */
    val sourcePackage: String? = null,
    /**
     * True when the source text came from an editable field, which is what makes
     * [OutputTarget.REPLACE_SELECTION] possible.
     */
    val isReplaceable: Boolean = false,
)
