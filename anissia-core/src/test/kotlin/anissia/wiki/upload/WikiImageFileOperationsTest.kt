package anissia.wiki.upload

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class WikiImageFileOperationsTest {
    @Test
    fun `disabled background processing does not move archived files during database recovery`() {
        val repository = mock(WikiImageRepository::class.java)
        val storage = mock(WikiImageStorage::class.java)
        WikiImageFileOperations(repository, storage, WikiUploadProperties(enabled = false)).recover()
        verifyNoInteractions(repository, storage)
    }
}
