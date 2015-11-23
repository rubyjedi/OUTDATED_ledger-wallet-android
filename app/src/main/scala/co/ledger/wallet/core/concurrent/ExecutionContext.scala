/**
 *
 * ExecutionContext
 * Ledger wallet
 *
 * Created by Pierre Pollastri on 02/02/15.
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
package co.ledger.wallet.core.concurrent

import java.util.concurrent.Executor

import android.app.Fragment
import android.content.Context
import android.os.{AsyncTask, Looper, Handler}
import co.ledger.wallet.core.base.UiContext

object ExecutionContext {

  object Implicits {
    implicit lazy val main = scala.concurrent.ExecutionContext.fromExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    implicit lazy val ui = new HandlerExecutionContext(new Handler(Looper.getMainLooper))
  }

  class HandlerExecutionContext(handler: Handler) extends scala.concurrent.ExecutionContextExecutor {
    override def execute(runnable: Runnable): Unit = {
      handler.post(runnable)
    }
    override def reportFailure(cause: Throwable): Unit = {}
  }


  class UiContextExecutionContext(context: UiContext) extends Executor {

    private val handler = new Handler(Looper.getMainLooper)

    override def execute(command: Runnable): Unit = {
      handler.post(new Runnable {
        override def run(): Unit = {
          if (context.isVisible)
            command.run()
        }
      })
    }
  }

}
