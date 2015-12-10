/**
 *
 * WalletDatabaseOpenHelper
 * Ledger wallet
 *
 * Created by Pierre Pollastri on 09/12/15.
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
package co.ledger.wallet.service.wallet.database

import android.content.Context
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import DatabaseStructure._

class WalletDatabaseOpenHelper(context: Context, walletName: String) extends
  SQLiteOpenHelper(context, walletName, null, 2) {

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {

  }

  override def onCreate(db: SQLiteDatabase): Unit = {
    CreateAccountsTable on db
    CreateAccountsTable on db
    CreateOperationsTable on db
    CreateInputsTable on db
    CreateOutputsTable on db
    CreateOperationsInputsTable on db
  }

  lazy val writer: WalletDatabaseWriter = new WalletDatabaseWriter(getWritableDatabase)
  lazy val reader: WalletDatabaseReader = new WalletDatabaseReader(getReadableDatabase)

  val CreateAccountsTable = {
    import DatabaseStructure.AccountTableColumns._
    s"""
       CREATE TABLE IF NOT EXISTS $AccountTableName(
       | $Index INTEGER PRIMARY KEY ASC,
       | $Name TEXT,
       | $Color INTEGER,
       | $Hidden INTEGER DEFAULT 0,
       | $Xpub58 TEXT NOT NULL,
       | $CreationTime INTEGER NOT NULL,
       |)
     """.stripMargin
  }

  val CreateOperationsTable = {
    import DatabaseStructure.OperationTableColumns._
    s"""
       CREATE TABLE IF NOT EXISTS $AccountTableName(
       | $Uid TEXT PRIMARY KEY,
       | $AccountId INTEGER NOT NULL,
       | $Hash TEXT NOT NULL,
       | $Fees INTEGER NOT NULL,
       | $Time INTEGER NOT NULL,
       | $LockTime INTEGER NOT NULL,
       | $Value INTEGER NOT NULL,
       | $Type INTEGER NOT NULL,
       | $BlockHash TEXT,
       | FOREIGN KEY($AccountId) REFERENCE $AccountTableName(${AccountTableColumns.Index}) ON
       | DELETE CASCADE
       |)
     """.stripMargin
  }

  val CreateInputsTable = {
    import DatabaseStructure.InputTableColumns._
    s"""
       CREATE TABLE IF NOT EXISTS $InputTableName(
       | $Uid TEXT PRIMARY KEY,
       | $Index INTEGER NOT NULL,
       | $Path TEXT,
       | $Value INTEGER NOT NULL,
       | $Coinbase INTEGER,
       | $PreviousTx TEXT NOT NULL,
       | $ScriptSig TEXT NOT NULL,
       | $Address TEXT
       |)
     """.stripMargin
  }

  val CreateOutputsTable = {
    import DatabaseStructure.OutputTableColumns._
    s"""
       CREATE TABLE IF NOT EXISTS $OutputTableName(
       | $Uid INTEGER PRIMARY KEY AUTOINCREMENT,
       | $Index INTEGER NOT NULL,
       | $TransactionHash TEXT NOT NULL,
       | $Path TEXT,
       | $Value INTEGER NOT NULL,
       | $PubKeyScript TEXT NOT NULL,
       | $Address TEXT,
       |)
     """.stripMargin
  }

  val CreateOperationsInputsTable = {
    import DatabaseStructure.OperationsInputsTableColumns._
    s"""
       CREATE TABLE IF NOT EXISTS $OutputTableName(
       | $OperationUid TEXT NOT NULL,
       | $InputUid TEXT NOT NULL
       |)
     """.stripMargin
  }

  private implicit class SqlString(val sql: String) {

    def on(db: SQLiteDatabase): Unit = {
      db.execSQL(sql)
    }

  }

}