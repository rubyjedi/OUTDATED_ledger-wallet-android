package co.ledger.wallet.app

import android.net.Uri
import co.ledger.wallet.{BaseConfig, BuildConfig}

/**
  *
  * Config
  * Ledger wallet
  *
  * Created by Pierre Pollastri on 12/06/15.
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

object Config extends BaseConfig {

  override def ApiBaseUri: Uri = Uri.parse("http://localhost:5000")
  override def WebSocketBaseUri: Uri = Uri.parse("ws://localhost:5000")
  override def WebSocketChannelsUri = WebSocketBaseUri.buildUpon().appendEncodedPath("2fa/channels")
    .build()
  override def LedgerAttestationPublicKey =
    "04e69fd3c044865200e66f124b5ea237c918503931bee070edfcab79a00a25d6b5a09afbee902b4b763ecf1f9c25f82d6b0cf72bce3faf98523a1066948f1a395f"
  override def HelpCenterUri = Uri.parse("http://support.ledgerwallet.com/help_center")
  override def SupportEmailAddress = "hello@ledger.fr"
  override def Env: String = "test"
  override def TrustletWebPage: Uri = Uri.parse("http://ledgerwallet.com/trustlet")
  override def DisableLogging = !BuildConfig.DEBUG

  override def GreenBitsPackageName = "com.greenaddress.greenbits_android_wallet"
  override def MyceliumPackageName = "com.mycelium.wallet"
 }
