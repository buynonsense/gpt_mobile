package dev.chungjungsoo.gptmobile.data.sync

import android.util.Log
import dev.chungjungsoo.gptmobile.data.sync.model.SyncErrorCategory
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavRepositoryImpl @Inject constructor(
    networkClient: dev.chungjungsoo.gptmobile.data.network.NetworkClient
) : WebDavRepository {

    private val client: HttpClient = networkClient()

    override suspend fun testConnection(config: WebDavConfig, password: String) {
        ensureDirectory(config, password)
    }

    override suspend fun listBackupFiles(config: WebDavConfig, password: String): List<WebDavRemoteFile> {
        ensureDirectory(config, password)
        val response = executeRequest {
            client.request(buildPath(config)) {
                method = HttpMethod("PROPFIND")
                header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
                header("Depth", "1")
                header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                setBody(PROPFIND_BODY)
            }
        }

        if (!response.status.isSuccess()) {
            throw toSyncOperationException(response.status)
        }

        val xml = response.bodyAsText()
        return WebDavXmlParser.parse(xml)
            .filter { it.name.endsWith(BACKUP_EXTENSION) && it.path.trimEnd('/').substringAfterLast('/') == it.name }
    }

    override suspend fun uploadBackup(config: WebDavConfig, password: String, fileName: String, content: String) {
        ensureDirectory(config, password)
        val response = executeRequest {
            client.put(buildFilePath(config, fileName)) {
                header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
                contentType(ContentType.Application.Json)
                setBody(content)
            }
        }

        if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.NoContent && !response.status.isSuccess()) {
            throw toSyncOperationException(response.status)
        }
    }

    override suspend fun downloadBackup(config: WebDavConfig, password: String, remotePath: String): String {
        val target = if (remotePath.startsWith("http://") || remotePath.startsWith("https://")) {
            remotePath
        } else {
            buildFilePath(config, remotePath.trimStart('/').substringAfterLast('/'))
        }
        val response = executeRequest {
            client.get(target) {
                header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
            }
        }

        if (!response.status.isSuccess()) {
            throw toSyncOperationException(response.status)
        }

        return response.bodyAsText()
    }

    private suspend fun ensureDirectory(config: WebDavConfig, password: String) {
        val path = buildPath(config)
        val propfindResponse = executeRequest {
            client.request(path) {
                expectSuccess = false
                method = HttpMethod("PROPFIND")
                header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
                header("Depth", "0")
                header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                setBody(PROPFIND_BODY)
            }
        }
        if (propfindResponse.status == HttpStatusCode.NotFound) {
            val createResponse = executeRequest {
                client.request(path) {
                    expectSuccess = false
                    method = HttpMethod("MKCOL")
                    header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
                }
            }
            if (createResponse.status != HttpStatusCode.Created && createResponse.status != HttpStatusCode.MethodNotAllowed) {
                throw toSyncOperationException(createResponse.status)
            }
            return
        }

        if (!propfindResponse.status.isSuccess() && propfindResponse.status != HttpStatusCode.MultiStatus) {
            Log.e("WebDavRepository", "WebDAV PROPFIND failed with status=${propfindResponse.status}")
            throw toSyncOperationException(propfindResponse.status)
        }
    }

    private suspend fun <T> executeRequest(block: suspend () -> T): T {
        return try {
            block()
        } catch (exception: RedirectResponseException) {
            throw toSyncOperationException(exception)
        } catch (exception: ClientRequestException) {
            throw toSyncOperationException(exception)
        } catch (exception: ServerResponseException) {
            throw toSyncOperationException(exception)
        } catch (exception: IOException) {
            throw toSyncOperationException(exception)
        }
    }

    private fun buildPath(config: WebDavConfig): String {
        val normalizedBase = config.baseUrl.trimEnd('/')
        val normalizedPath = config.remotePath.trim().trim('/').takeIf { it.isNotEmpty() }

        return if (normalizedPath == null) {
            "$normalizedBase/"
        } else {
            "$normalizedBase/$normalizedPath/"
        }
    }

    private fun buildFilePath(config: WebDavConfig, fileName: String): String = buildPath(config) + fileName

    private fun basicAuthorization(username: String, password: String): String {
        val raw = "$username:$password"
        return "Basic ${raw.encodeToByteArray().encodeBase64()}"
    }

    companion object {
        internal fun toSyncOperationException(status: HttpStatusCode): SyncOperationException {
            val category = when (status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden -> SyncErrorCategory.WEBDAV_AUTH_FAILED

                else -> SyncErrorCategory.WEBDAV_SERVER_ERROR
            }

            return SyncOperationException(category)
        }

        internal fun toSyncOperationException(throwable: Throwable): SyncOperationException {
            return when (throwable) {
                is SyncOperationException -> throwable
                is RedirectResponseException -> toSyncOperationException(throwable.response.status)
                is ClientRequestException -> toSyncOperationException(throwable.response.status)
                is ServerResponseException -> toSyncOperationException(throwable.response.status)
                is IOException -> SyncOperationException(SyncErrorCategory.WEBDAV_NETWORK_ERROR, throwable)
                else -> SyncOperationException(SyncErrorCategory.WEBDAV_SERVER_ERROR, throwable)
            }
        }

        private const val BACKUP_EXTENSION = ".json"
        private const val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname />
                <d:getcontentlength />
                <d:getlastmodified />
                <d:getetag />
              </d:prop>
            </d:propfind>
        """
    }
}
