package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import io.ktor.http.HttpStatusCode
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavRepositoryImplErrorTest {
    @Test
    fun toSyncOperationException_401_mapsToAuthFailed() {
        val error = WebDavRepositoryImpl.toSyncOperationException(HttpStatusCode.Unauthorized)

        assertEquals(SyncErrorCategory.WEBDAV_AUTH_FAILED, error.category)
    }

    @Test
    fun toSyncOperationException_403_mapsToAuthFailed() {
        val error = WebDavRepositoryImpl.toSyncOperationException(HttpStatusCode.Forbidden)

        assertEquals(SyncErrorCategory.WEBDAV_AUTH_FAILED, error.category)
    }

    @Test
    fun toSyncOperationException_302_mapsToServerError() {
        val error = WebDavRepositoryImpl.toSyncOperationException(HttpStatusCode.Found)

        assertEquals(SyncErrorCategory.WEBDAV_SERVER_ERROR, error.category)
    }

    @Test
    fun toSyncOperationException_503_mapsToServerError() {
        val error = WebDavRepositoryImpl.toSyncOperationException(HttpStatusCode.ServiceUnavailable)

        assertEquals(SyncErrorCategory.WEBDAV_SERVER_ERROR, error.category)
    }

    @Test
    fun toSyncOperationException_ioFailure_mapsToNetworkError() {
        val error = WebDavRepositoryImpl.toSyncOperationException(IOException("timeout"))

        assertEquals(SyncErrorCategory.WEBDAV_NETWORK_ERROR, error.category)
    }
}
