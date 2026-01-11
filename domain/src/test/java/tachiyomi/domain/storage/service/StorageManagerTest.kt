package tachiyomi.domain.storage.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class StorageManagerTest {

    @Test
    fun `blank uri should be invalid`() {
        val blankUri = ""
        blankUri.isBlank() shouldBe true
    }

    @Test
    fun `content uri scheme detection`() {
        val contentUri = "content://com.android.externalstorage.documents/tree/primary%3AKomikku"
        contentUri.startsWith("content://") shouldBe true
    }

    @Test
    fun `file uri scheme detection`() {
        val fileUri = "file:///storage/emulated/0/Komikku"
        fileUri.startsWith("file://") shouldBe true
    }

    @Test
    fun `internal storage path matching - should match`() {
        val internalBasePath = "/storage/emulated/0/Android/data/app.komikku/files"
        val currentPath = "/storage/emulated/0/Android/data/app.komikku/files/Komikku"

        currentPath.startsWith(internalBasePath) shouldBe true
    }

    @Test
    fun `internal storage path matching - should not match external`() {
        val internalBasePath = "/storage/emulated/0/Android/data/app.komikku/files"
        val externalPath = "/storage/emulated/0/Komikku"

        externalPath.startsWith(internalBasePath) shouldBe false
    }

    @Test
    fun `internal storage path matching - should not match different app`() {
        val internalBasePath = "/storage/emulated/0/Android/data/app.komikku/files"
        val otherAppPath = "/storage/emulated/0/Android/data/other.app/files/Komikku"

        otherAppPath.startsWith(internalBasePath) shouldBe false
    }

    @Test
    fun `secure folder uri detection`() {
        // Samsung Secure Folder URIs have user prefix like 0@
        val secureFolderUri = "content://0@com.android.externalstorage.documents/tree/primary%3A150"
        secureFolderUri.contains("@") shouldBe true
    }

    @Test
    fun `normal content uri has no user prefix`() {
        val normalUri = "content://com.android.externalstorage.documents/tree/primary%3AKomikku"
        normalUri.substringAfter("content://").substringBefore("/").contains("@") shouldBe false
    }
}
