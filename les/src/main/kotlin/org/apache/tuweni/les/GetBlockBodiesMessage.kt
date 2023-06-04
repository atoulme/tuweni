// Copyright The Tuweni Authors
// SPDX-License-Identifier: Apache-2.0
package org.apache.tuweni.les

import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.eth.Hash
import org.apache.tuweni.rlp.RLP

internal data class GetBlockBodiesMessage(val reqID: Long, val blockHashes: List<Hash>) {

  fun toBytes(): Bytes {
    return RLP.encodeList { writer ->
      writer.writeLong(reqID)
      writer.writeList(blockHashes) { eltWriter, hash -> eltWriter.writeValue(hash) }
    }
  }

  companion object {

    fun read(bytes: Bytes): GetBlockBodiesMessage {
      return RLP.decodeList(
        bytes,
      ) { reader ->
        GetBlockBodiesMessage(
          reader.readLong(),
          reader.readListContents { elementReader -> Hash.fromBytes(elementReader.readValue()) },
        )
      }
    }
  }
}
