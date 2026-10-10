package com.gios.brightlibrarylite

import com.gios.brightlibrarylite.data.AppFiles
import com.gios.brightlibrarylite.data.Library
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

/**
 * How books get onto the phone.
 *
 * The tool declares one folder to Light's Tool Manager: "Books", backed by `files/shared/Books`.
 * The Tool Manager shows it in a browser on the computer; files dropped there are written straight
 * into that directory, and LightOS then runs [onToolManagerDataUpdate] so the tool can react.
 */
@EntryPoint
object ToolEntryPoint : LightEntryPoint {

    private val booksFolder = FileBrowserSpec(
        label = "Books",
        path = AppFiles.BOOKS_FOLDER,
        headerText = "Drop EPUB, MOBI (AZW, AZW3) or CBZ files here. They appear on the " +
            "BrightLibrary Lite shelf the next time it is open. Delete a file here to take the " +
            "book off the shelf. DRM-protected books cannot be read.",
    )

    override fun getToolManagerManifest(): ClientToolManifest = ClientToolManifest(
        title = "BrightLibrary Lite",
        roots = listOf(ClientLeafNode(booksFolder)),
    )

    /**
     * Import whatever changed. This runs in a WorkManager worker, which allows minutes of work —
     * enough for a comic's page conversion.
     *
     * The tool's files directory is only known once a screen has been created (the SDK hands it to
     * screens, not to the entry point), so if the tool has not been opened since the phone started
     * there is nothing to do here: the shelf sweeps the folder itself every time it opens.
     */
    override suspend fun onToolManagerDataUpdate() {
        super.onToolManagerDataUpdate()
        if (AppFiles.isReady) Library.sync()
    }
}
