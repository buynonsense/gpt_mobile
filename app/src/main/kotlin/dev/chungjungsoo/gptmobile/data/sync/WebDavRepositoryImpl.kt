package dev.chungjungsoo.gptmobile.data.sync

import android.util.Log
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavConfig
import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import io.ktor.client.HttpClient
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
        val response = client.request(buildPath(config)) {
            method = HttpMethod("PROPFIND")
            header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
            header("Depth", "1")
            header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            setBody(PROPFIND_BODY)
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to list WebDAV files: ${response.status}")
        }

        val xml = response.bodyAsText()
        return WebDavXmlParser.parse(xml)
            .filter { it.name.endsWith(BACKUP_EXTENSION) && it.path.trimEnd('/').substringAfterLast('/') == it.name }
    }

    override suspend fun uploadBackup(config: WebDavConfig, password: String, fileName: String, content: String) {
        ensureDirectory(config, password)
        val response = client.put(buildFilePath(config, fileName)) {
            header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
            contentType(ContentType.Application.Json)
            setBody(content)
        }

        if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.NoContent && !response.status.isSuccess()) {
            throw IllegalStateException("Failed to upload backup: ${response.status}")
        }
    }

    override suspend fun downloadBackup(config: WebDavConfig, password: String, remotePath: String): String {
        val target = if (remotePath.startsWith("http://") || remotePath.startsWith("https://")) {
            remotePath
        } else {
            buildFilePath(config, remotePath.trimStart('/').substringAfterLast('/'))
        }
        val response = client.get(target) {
            header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to download backup: ${response.status}")
        }

        return response.bodyAsText()
    }

    private suspend fun ensureDirectory(config: WebDavConfig, password: String) {
        val path = buildPath(config)
        val propfindResponse = client.request(path) {
            method = HttpMethod("PROPFIND")
            header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
            header("Depth", "0")
            header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            setBody(PROPFIND_BODY)
        }
        if (propfindResponse.status == HttpStatusCode.NotFound) {
            val createResponse = client.request(path) {
                method = HttpMethod("MKCOL")
                header(HttpHeaders.Authorization, basicAuthorization(config.username, password))
            }
            if (createResponse.status != HttpStatusCode.Created && createResponse.status != HttpStatusCode.MethodNotAllowed) {
                throw IllegalStateException("Failed to create WebDAV directory: ${createResponse.status}")
            }
            return
        }

        if (!propfindResponse.status.isSuccess() && propfindResponse.status != HttpStatusCode.MultiStatus) {
            Log.e("WebDavRepository", "WebDAV PROPFIND failed with status=${propfindResponse.status}")
            throw IllegalStateException("Failed to access WebDAV directory: ${propfindResponse.status}")
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
