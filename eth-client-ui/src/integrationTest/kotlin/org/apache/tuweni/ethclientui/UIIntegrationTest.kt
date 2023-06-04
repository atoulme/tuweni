// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.ethclientui

import io.vertx.core.Vertx
import kotlinx.coroutines.runBlocking
import org.apache.tuweni.ethclient.EthereumClient
import org.apache.tuweni.ethclient.EthereumClientConfig
import org.apache.tuweni.junit.BouncyCastleExtension
import org.apache.tuweni.junit.TempDirectory
import org.apache.tuweni.junit.TempDirectoryExtension
import org.apache.tuweni.junit.VertxExtension
import org.apache.tuweni.junit.VertxInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path

@ExtendWith(VertxExtension::class, BouncyCastleExtension::class, TempDirectoryExtension::class)
class UIIntegrationTest {

  @Test
  fun testServerComesUp(@VertxInstance vertx: Vertx, @TempDirectory tempDir: Path) = runBlocking {
    val client = EthereumClient(
      vertx,
      EthereumClientConfig.fromString(
        """[storage.default]
path="${tempDir.toAbsolutePath()}"
genesis="default"
[genesis.default]
path="classpath:/genesis/dev.json"
[peerRepository.default]
type="memory"""",
      ),
    )
    val ui = ClientUIApplication.start(client = client, port = 9000)
    client.start()
    ui.start()
    val app = ui.getBean(ClientUIApplication::class.java)
    val url = URL("http://localhost:" + app.config.port)
    val con = url.openConnection() as HttpURLConnection
    con.requestMethod = "GET"
    val response = con.inputStream.readAllBytes()
    assertTrue(response.isNotEmpty())

    val url2 = URL("http://localhost:" + app.config.port + "/rest/config")
    val con2 = url2.openConnection() as HttpURLConnection
    con2.requestMethod = "GET"
    val response2 = con2.inputStream.readAllBytes()
    assertTrue(response2.isNotEmpty())
    val url3 = URL("http://localhost:" + app.config.port + "/rest/state")
    val con3 = url3.openConnection() as HttpURLConnection
    con3.requestMethod = "GET"
    val response3 = con3.inputStream.readAllBytes()
    assertTrue(response3.isNotEmpty())
    assertEquals(
      """{"peerCounts":{"default":0},""" +
        """"bestBlocks":{"default":{"hash":"0xa08d1edb37ba1c62db764ef7c2566cbe368b850f5b3762c6c24114a3fd97b87f",""" +
        """"number":"0x0000000000000000000000000000000000000000000000000000000000000000"}}}""",
      String(response3),
    )
    ui.stop()
  }
}
