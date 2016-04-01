/**
  *
  * ApiAccount
  * Ledger wallet
  *
  * Created by Pierre Pollastri on 17/03/16.
  *
  * The MIT License (MIT)
  *
  * Copyright (c) 2015 Ledger
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  *
  */
package co.ledger.wallet.service.wallet.api

import java.io.{BufferedInputStream, FileInputStream, FileReader, File}

import co.ledger.wallet.core.utils.Preferences
import co.ledger.wallet.core.utils.io.IOUtils
import co.ledger.wallet.core.utils.logs.{Logger, Loggable}
import co.ledger.wallet.service.wallet.AbstractDatabaseStoredAccount
import co.ledger.wallet.service.wallet.api.rest.ApiObjects
import co.ledger.wallet.service.wallet.api.rest.ApiObjects.Block
import co.ledger.wallet.service.wallet.database.DatabaseStructure.OperationTableColumns
import co.ledger.wallet.service.wallet.database.{DatabaseStructure, WalletDatabaseWriter}
import co.ledger.wallet.service.wallet.database.model.{OperationRow, TransactionRow, AccountRow}
import co.ledger.wallet.service.wallet.database.proxy.{TransactionOutputProxy, TransactionInputProxy, TransactionProxy}
import co.ledger.wallet.service.wallet.database.utils.DerivationPathBag
import co.ledger.wallet.wallet.{DerivationPath, ExtendedPublicKeyProvider}
import co.ledger.wallet.wallet.api.ApiWalletClientProtos
import com.google.protobuf.nano.{CodedOutputByteBufferNano, CodedInputByteBufferNano}
import org.bitcoinj.core.{Coin, Transaction}
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.wallet.{Protos, DeterministicKeyChain}
import scala.collection.JavaConversions._
import co.ledger.wallet.wallet._
import co.ledger.wallet.wallet.events.PeerGroupEvents._
import co.ledger.wallet.wallet.events.WalletEvents._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class ApiAccountClient(val wallet: ApiWalletClient, row: AccountRow)
  extends AbstractDatabaseStoredAccount(wallet) with Loggable {

  private val BatchSize = 40

  override def keyChain: DeterministicKeyChain = _keychain

  override def rawTransaction(hash: String): Future[Transaction] = ???

  //
  // Synchronization methods
  //

  override def synchronize(provider: ExtendedPublicKeyProvider): Future[Unit] = wallet
    .synchronize(provider)

  def synchronize(syncToken: String, block: ApiObjects.Block): Future[Unit] = {
    // Load previously saved state
    // Fetch all unconfirmed transactions
    // Synchronize all previous batches
      // If batches.length == 0 or last batches is not empty synchronize another batch
        // If batches not empty redo the last step
    // Remove not found unconfirmed transactions
    // Everything went ok. Save the state and normalize batches block with the block in parameter
    // Save the state

    load() flatMap {(savedState) =>
      var unconfirmedTransaction = OperationRow(databaseWallet.databaseReader
        .unconfirmedAccountOperations(index)).map(_.transactionHash)
      def synchronizeBatchUntilEmpty(from: Int = 0): Future[Unit] = {
        synchronizeBatches(syncToken, savedState, 0, savedState.batches.length - 1).flatMap {
          (fetchedTxs) =>
          unconfirmedTransaction = unconfirmedTransaction.filter(!fetchedTxs.contains(_))
          if (savedState.batches.isEmpty || savedState.batches.last.blockHash == null) {
            synchronizeBatchUntilEmpty(savedState.batches.length + 1)
          } else {
            Future.successful()
          }
        }
      }
      synchronizeBatchUntilEmpty().flatMap(_ => save(savedState))
    } map {(_) =>
      ()
    }
  }

  def synchronizeBatches(syncToken: String,
                         savedState: ApiWalletClientProtos.ApiAccountClient,
                         from: Int, to: Int):
  Future[Array[String]] = {

    def synchronizeBatch(batchIndex: Int): Future[Array[String]] = {
      val fromAddress = batchIndex * BatchSize
      val toAddress = fromAddress + (BatchSize - 1)
      val hashes =  new ArrayBuffer[String]()
      def synchronizeBatchUntilEmpty(): Future[Array[String]] = {
        val batch = savedState.batches(batchIndex)
        val addresses = new ArrayBuffer[String]()
        for (addressIndex <- fromAddress to toAddress) {
          import DerivationPath.dsl._
          val externalPath = 0.h / 0 / addressIndex
          val internalPath = 0.h / 1 / addressIndex
          keyChain.maybeLookAhead()
          addresses += keyChain.getKeyByPath(externalPath.toBitcoinJList, true)
            .toAddress(wallet.networkParameters).toString
          addresses += keyChain.getKeyByPath(internalPath.toBitcoinJList, true)
            .toAddress(wallet.networkParameters).toString
        }
        val blockHash = if (batch.blockHash != null && batch.blockHash.nonEmpty) Some(batch
          .blockHash) else None
        wallet.transactionRestClient.transactions(syncToken, addresses.toArray, blockHash) flatMap {
          (result) =>
          val lastBlock = pushTransactions(result.transactions, hashes)
          if (lastBlock.isDefined) {
            batch.blockHash = lastBlock.get.hash
            batch.blockHeight = lastBlock.get.height.toInt
          }
          save(savedState) flatMap {unit =>
            if (result.isTruncated)
              synchronizeBatchUntilEmpty()
            else
              Future.successful(hashes.toArray)
          }
        }
      }
      while (batchIndex >= savedState.batches.length) {
        val newBatch = new ApiWalletClientProtos.ApiAccountClient.Batch()
        newBatch.index = savedState.batches.length
        savedState.batches = savedState.batches :+ newBatch
      }
      synchronizeBatchUntilEmpty()
    }

    Future.sequence((from to Math.max(from, to)).map(synchronizeBatch)).map {(result) =>
      result.flatten.toArray
    }
  }

  private def pushTransactions(transactions: Array[ApiObjects.Transaction],
                              hashes: ArrayBuffer[String]): Option[ApiObjects.Block] = {
    val writer = wallet.databaseWriter
    writer.beginTransaction()
    var lastBlock: Option[ApiObjects.Block] = None
    for (transaction <- transactions) {
      if (lastBlock.map(_.height).getOrElse(0L) < transaction.block.map(_.height).getOrElse(0L))
        lastBlock = transaction.block
      pushTransaction(transaction, hashes)
    }
    writer.endTransaction()
    lastBlock
  }

  private def pushTransaction(tx: ApiObjects.Transaction, hashes: ArrayBuffer[String]): Unit = {
    val writer = wallet.databaseWriter
    val transaction = new TransactionHelper(tx)
    writer.updateOrCreateTransaction(transaction.proxy, transaction.bag)
    hashes += transaction.proxy.hash
    val isSend = computeSendOperation(transaction, writer)
    computeReceiveOperation(transaction, writer, !isSend)
  }

  private def computeSendOperation(tx: TransactionHelper, writer: WalletDatabaseWriter): Boolean = {
    /*
    updateOrCreateOperation(accountId: Int,
      transactionHash: String,
      operationType: Int,
      value: Long,
      senders: Array[String],
      recipients: Array[String])
     */
    if (tx.hasOwnInputs) {
      val inserted = writer.updateOrCreateOperation(
        index,
        tx.proxy.hash,
        DatabaseStructure.OperationTableColumns.Types.Send,
        tx.sentValue.getValue,
        tx.senders,
        tx.recipients)
      if (inserted) {
        wallet.eventBus.post(NewOperation(index, databaseWallet.querySingleOperation(
          index,
          tx.proxy.hash,
          OperationTableColumns.Types.Send
        )))
      } else {
        wallet.eventBus.post(OperationChanged(index, databaseWallet.querySingleOperation(
          index,
          tx.proxy.hash,
          OperationTableColumns.Types.Send
        )))
      }
      true
    } else {
      false
    }
  }

  private def computeReceiveOperation(tx: TransactionHelper,
                                      writer: WalletDatabaseWriter,
                                      forceReceive: Boolean): Unit = {
    if (forceReceive || (tx.ownOutputs.length == tx.proxy.outputs.length) || tx.hasOwnExternalOutputs) {
      val inserted = writer.updateOrCreateOperation(
        index,
        tx.proxy.hash,
        DatabaseStructure.OperationTableColumns.Types.Reception,
        tx.receivedValue.getValue,
        tx.senders,
        tx.recipients)
      if (inserted) {
        wallet.eventBus.post(NewOperation(index, databaseWallet.querySingleOperation(
          index,
          tx.proxy.hash,
          OperationTableColumns.Types.Reception
        )))
      } else {
        wallet.eventBus.post(OperationChanged(index, databaseWallet.querySingleOperation(
          index,
          tx.proxy.hash,
          OperationTableColumns.Types.Reception
        )))
      }
    }
  }

  //
  // \Synchronization methods
  //

  override def xpub(): Future[DeterministicKey] = Future.successful(_xpub)

  override def index: Int = row.index

  def load(): Future[ApiWalletClientProtos.ApiAccountClient] = Future {
    if (savedStateFile.exists()) {
      val input = IOUtils.copy(savedStateFile)
      ApiWalletClientProtos.ApiAccountClient.parseFrom(input)
    } else {
      val state = new ApiWalletClientProtos.ApiAccountClient()
      state.index = index
      state.batches = ApiWalletClientProtos.ApiAccountClient.Batch.emptyArray()
      state.batchSize = BatchSize
      state
    }
  }

  def save(savedState: ApiWalletClientProtos.ApiAccountClient): Future[Unit] = Future {
    val raw = new Array[Byte](savedState.getSerializedSize)
    val output = CodedOutputByteBufferNano.newInstance(raw)
    savedState.writeTo(output)
    val tmpFile = new File(directory, "tmp_saved_state")
    IOUtils.copy(raw, tmpFile)
    tmpFile.renameTo(savedStateFile)
  }

  private val _preferences = Preferences(s"ApiAccountClient_$index")(wallet.context)
  private val _xpub = {
    val accountKey = DeterministicKey.deserializeB58(row.xpub58, wallet.networkParameters)
    new DeterministicKey(
      DeterministicKeyChain.ACCOUNT_ZERO_PATH,
      accountKey.getChainCode,
      accountKey.getPubKeyPoint,
      null, null)
  }

  val directory = new File(wallet.directory, s"account_$index/")
  directory.mkdirs()

  private val _keychain = {
    val keychainFile = new File(directory, "keychain")
    if (!keychainFile.exists()) {
      DeterministicKeyChain.watch(_xpub)
    } else {
      try {
        val reader = new BufferedInputStream(new FileInputStream(keychainFile))
        var size = 0
        val keys = new ArrayBuffer[Protos.Key]()
        def readSize(): Int = {
          val b1 = reader.read()
          val b2 = reader.read()
          if (b1 == -1 || b2 == -1) -1 else b1 << 8 | b2
        }
        while ({size = readSize(); size} != -1) {
          val bytes = new Array[Byte](size)
          reader.read(bytes, 0, bytes.length)
          keys += Protos.Key.parseFrom(bytes)
        }
        DeterministicKeyChain.fromProtobuf(keys.toList, null, null).get(0)
      } catch {
        case anything: Throwable =>
          anything.printStackTrace()
          DeterministicKeyChain.watch(_xpub)
      }
    }
  }

  private val savedStateFile = new File(directory, "saved_state")

  class TransactionHelper(tx: ApiObjects.Transaction) {
    val proxy = TransactionProxy(tx)
    val bag = {
      val b = new DerivationPathBag
      b.inflate(proxy, _keychain, index)
      b
    }

    val ownInputs = proxy.inputs.filter(bag.findPath(_).isDefined)
    val ownOutputs = proxy.outputs.filter(bag.findPath(_).isDefined)

    def hasOwnInputs = ownInputs.nonEmpty
    def hasOwnOutputs = ownOutputs.nonEmpty
    def hasOwnExternalOutputs = ownOutputs exists isExternal

    def sentValue: Coin = {
      var total = Coin.ZERO
      for (input <- ownInputs) {
        total = total add input.value.get
      }
      var hasChange = false
      for (output <- ownOutputs) {
        if (isInternal(output)) {
          if (!hasChange)
            hasChange = true
          else
            total = total add output.value
        }
      }
      total
    }

    def receivedValue: Coin = {
      if (ownOutputs.length == 1)
        ownOutputs.head.value
      else {
        var total = Coin.ZERO
        var hasChange = false
        for (output <- ownOutputs) {
          if (isInternal(output) && !hasChange) {
           hasChange = true
          } else {
            total = total add output.value
          }
        }
        total
      }
    }

    def senders: Array[String] = {
      (proxy.inputs.filter {(input) =>
        input.address.isDefined || input.isCoinbase
      } map {(input) =>
        input.address.getOrElse("coinbase")
      }).toArray
    }

    def recipients: Array[String] =  {
      (proxy.outputs.filter {(output) =>
        output.address.isDefined
      } map {(output) =>
        output.address.get
      }).toArray
    }

    private def isInternal(input: TransactionInputProxy): Boolean = {
      bag.findPath(input) exists isInternal
    }

    private def isInternal(output: TransactionOutputProxy): Boolean = {
      bag.findPath(output) exists isInternal
    }

    private def isInternal(path: DerivationPath): Boolean = path(1).get.childNum == 1

    private def isExternal(output: TransactionOutputProxy): Boolean = {
      bag.findPath(output) exists isExternal
    }

    private def isExternal(path: DerivationPath): Boolean = path(1).get.childNum == 0
  }

}
